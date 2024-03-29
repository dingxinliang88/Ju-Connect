package com.juzi.gateway.filter;

import com.juzi.common.biz.StatusCode;
import com.juzi.common.util.ThrowUtils;
import com.juzi.dubbo.service.RPCInterfaceService;
import com.juzi.dubbo.service.RPCUserInterfaceService;
import com.juzi.dubbo.service.RPCUserService;
import com.juzi.gateway.manager.RedisLimiterManager;
import com.juzi.model.dto.interface_info.InterfaceGatewayQueryRequest;
import com.juzi.model.dto.user_interface_info.UserInterfaceAccNumDownRequest;
import com.juzi.model.entity.InterfaceInfo;
import com.juzi.model.entity.User;
import com.juzi.model.enums.ApiMethodEnums;
import com.juzi.sdk.utils.SignUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.reactivestreams.Publisher;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import javax.annotation.Resource;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * @author codejuzi
 */
@Slf4j
@Component
public class CustomGlobalFilter implements GlobalFilter, Ordered {

    @DubboReference
    private RPCInterfaceService rpcInterfaceService;

    @DubboReference
    private RPCUserService rpcUserService;

    @DubboReference
    private RPCUserInterfaceService rpcUserInterfaceService;

    @Resource
    private RedisLimiterManager redisLimiterManager;

    private static final List<String> IP_WHITE_LIST = Arrays.asList(
            "127.0.0.1",
            "192.168.0.101",
            "0:0:0:0:0:0:0:1"
    );

    private static final String INTERFACE_URL_HOST = "http://localhost:8200";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // 请求日志处理
        ServerHttpRequest request = exchange.getRequest();
        doReqLog(request);
        // 黑白名单
        ServerHttpResponse response = exchange.getResponse();
        String sourceAddr = Objects.requireNonNull(request.getRemoteAddress()).getHostString();
        if (!IP_WHITE_LIST.contains(sourceAddr)) {
            response.setStatusCode(HttpStatus.FORBIDDEN);
            return response.setComplete();
        }

        // 用户鉴权
        User invokeUser;
        try {
            invokeUser = doUserAuth(request);
        } catch (Exception e) {
            return handleNoAuth(response);
        }

        // 限流
        boolean limitRes = doLimiter(invokeUser);
        if (!limitRes) {
            log.info("userId: {} 请求频繁", invokeUser.getId());
            response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
            return response.setComplete();
        }

        // 请求的模拟接口是否存在
        String methodStr = request.getMethodValue();
        String apiUrl = INTERFACE_URL_HOST + request.getPath().value();
        InterfaceInfo interfaceInfo;
        try {
            interfaceInfo = queryInterfaceInfo(apiUrl, methodStr);
        } catch (Exception e) {
            log.error("接口调用异常", e);
            return handleNoAuth(response);
        }
        if (Objects.isNull(interfaceInfo)) {
            return handleNoAuth(response);
        }

        // 请求转发，调用模拟接口
        return handleResponse(exchange, chain, invokeUser.getId(), interfaceInfo.getId());
    }

    private Mono<Void> handleResponse(ServerWebExchange exchange,
                                      GatewayFilterChain chain,
                                      Long userId,
                                      Long interfaceInfoId) {
        try {
            // 初始的响应对象
            ServerHttpResponse originalResponse = exchange.getResponse();
            // 缓冲区工厂，拿到缓存数据
            DataBufferFactory bufferFactory = originalResponse.bufferFactory();
            HttpStatus statusCode = originalResponse.getStatusCode();

            if (statusCode == HttpStatus.OK) {
                // 装饰，增强能力
                ServerHttpResponseDecorator decoratedResponse = new ServerHttpResponseDecorator(originalResponse) {
                    // 等调用完转发的接口后才会执行
                    @SuppressWarnings("NullableProblems")
                    @Override
                    public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
                        log.info("body instanceof Flux: {}", (body instanceof Flux));
                        if (body instanceof Flux) {
                            // 对象是响应式的
                            Flux<? extends DataBuffer> fluxBody = Flux.from(body);
                            return super.writeWith(fluxBody.map(dataBuffer -> {
                                // 扣减用户接口调用次数
                                UserInterfaceAccNumDownRequest userInterfaceAccNumDownRequest
                                        = new UserInterfaceAccNumDownRequest(userId, interfaceInfoId, 1);
                                rpcUserInterfaceService.userInterfaceAccNumDown(userInterfaceAccNumDownRequest);
                                byte[] content = new byte[dataBuffer.readableByteCount()];
                                dataBuffer.read(content);
                                // 释放内存
                                DataBufferUtils.release(dataBuffer);
                                // 构建日志
                                StringBuilder logBuilder = new StringBuilder(200);
                                logBuilder.append("<--- {} {} \n");
                                List<Object> rspArgs = new ArrayList<>();
                                rspArgs.add(originalResponse.getStatusCode());
//                                rspArgs.add(requestUrl);
                                String data = new String(content, StandardCharsets.UTF_8);
                                logBuilder.append(data);
                                log.info(logBuilder.toString(), rspArgs.toArray());
                                return bufferFactory.wrap(content);
                            }));
                        } else {
                            // 调用失败，返回错误的验证码
                            log.error("<--- {} 响应code异常", getStatusCode());
                        }
                        return super.writeWith(body);
                    }
                };

                return chain.filter(exchange.mutate().response(decoratedResponse).build());
            }
            // 降级处理返回数据
            return chain.filter(exchange);
        } catch (Exception e) {
            log.error("gateway log exception.\n" + e);
            return chain.filter(exchange);
        }
    }

    @Override
    public int getOrder() {
        return -1;
    }

    private boolean doLimiter(User loginUser) {

        final String USER_METHOD_RATE_PREFIX = "invoke_interface_";
        // 每个用户一个限流器
        return redisLimiterManager.doRateLimit(USER_METHOD_RATE_PREFIX + loginUser.getId());
    }

    private void doReqLog(ServerHttpRequest request) {
        log.info("请求唯一标识：{}", request.getId());
        log.info("请求路径：{}", request.getPath().value());
        log.info("请求方法类型：{}", request.getMethod());
        log.info("请求参数：{}", request.getQueryParams());
        log.info("请求IP：{}", request.getRemoteAddress());
    }

    private User doUserAuth(ServerHttpRequest request) {
        HttpHeaders headers = request.getHeaders();
        String accessKey = headers.getFirst("accessKey");
        String nonce = headers.getFirst("nonce");
        String timestamp = headers.getFirst("timestamp");
        String body;
        try {
            body = URLDecoder.decode(Objects.requireNonNull(headers.getFirst("body")), "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
        String sign = headers.getFirst("sign");

        User invokeUser = rpcUserService.getUserByAccessKey(accessKey);
        if (Objects.isNull(invokeUser)) {
            throw new RuntimeException("无权限");
        }

        // 实际上是根据ak去数据库查询是否分配给用户
        if (!invokeUser.getAccessKey().equals(accessKey)) {
            throw new RuntimeException("无权限");
        }

        // todo 校验随机数，实际上还要查看服务器端是否有这个随机数，可以使用Redis存储
        assert nonce != null;
        if (nonce.length() != 20) {
            throw new RuntimeException("无权限");
        }

        // 校验时间戳，和当前时间相差5分钟以内
        if (!validateTimestamp(timestamp)) {
            throw new RuntimeException("无权限");
        }

        // 校验sign， 实际上的sk是从数据库中查出来的
        String serverSign = SignUtils.genSign(body, invokeUser.getSecretKey());
        if (!serverSign.equals(sign)) {
            throw new RuntimeException("无权限");
        }
        return invokeUser;
    }

    private InterfaceInfo queryInterfaceInfo(String apiUrl, String methodStr) {
        ApiMethodEnums apiMethodEnum = ApiMethodEnums.getEnumByMethod(methodStr);
        ThrowUtils.throwIf(Objects.isNull(apiMethodEnum), StatusCode.PARAMS_ERROR, "非法请求方法");
        Integer apiMethod = apiMethodEnum.getApiMethod();
        InterfaceGatewayQueryRequest interfaceGatewayQueryRequest = new InterfaceGatewayQueryRequest(apiUrl, apiMethod);
        return rpcInterfaceService.queryInterfaceByGateway(interfaceGatewayQueryRequest);
    }

    private Mono<Void> handleNoAuth(ServerHttpResponse response) {
        response.setStatusCode(HttpStatus.FORBIDDEN);
        return response.setComplete();
    }

    private boolean validateTimestamp(String timestamp) {
        long currentTime = Instant.now().getEpochSecond();
        long inputTime = Long.parseLong(timestamp);

        long timeDifference = currentTime - inputTime;
        long timeDifferenceInMinutes = Math.abs(timeDifference) / 60;

        return timeDifferenceInMinutes <= 5;
    }
}
