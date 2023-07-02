package com.juzi.web.mapper;


import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.juzi.model.entity.User;
import org.apache.ibatis.annotations.Mapper;

/**
* @author codejuzi
* @description 针对表【user(用户表)】的数据库操作Mapper
* @createDate 2023-06-27 14:48:36
*/
@Mapper
public interface UserMapper extends BaseMapper<User> {

}




