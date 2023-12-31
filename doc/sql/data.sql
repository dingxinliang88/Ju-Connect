INSERT INTO interface_info (apiName, apiUrl, reqParam, reqMethod, reqHeader, respHeader, userId)
VALUES ('User Registration', 'https://api.example.com/register', '[{"type": "String", "arg": "username"}]', 1,
        '{"Content-Type": "application/json", "Authorization": "Bearer token123"}',
        '{"Content-Type": "application/json"}', 1),
       ('User Login', 'https://api.example.com/login', '[{"type": "String", "arg": "username"}]', 1,
        '{"Content-Type": "application/json"}',
        '{"Content-Type": "application/json", "Authorization": "Bearer token456"}', 1),
       ('Get User Profile', 'https://api.example.com/user/profile', null, 0,
        '{"Content-Type": "application/json", "Authorization": "Bearer token789"}',
        '{"Content-Type": "application/json"}', 1),
       ('Update User Profile', 'https://api.example.com/user/profile', '[{"type": "String", "arg": "username"}]', 2,
        '{"Content-Type": "application/json", "Authorization": "Bearer token789"}',
        '{"Content-Type": "application/json"}', 1),
       ('Delete User Account', 'https://api.example.com/user/account', null, 3,
        '{"Content-Type": "application/json", "Authorization": "Bearer token789"}',
        '{"Content-Type": "application/json"}', 1),
       ('Create Product', 'https://api.example.com/product', '[{"type": "String", "arg": "username"}]', 1,
        '{"Content-Type": "application/json", "Authorization": "Bearer token123"}',
        '{"Content-Type": "application/json"}', 1),
       ('Get Product Details', 'https://api.example.com/product/123', null, 0,
        '{"Content-Type": "application/json", "Authorization": "Bearer token456"}',
        '{"Content-Type": "application/json"}', 1),
       ('Update Product Details', 'https://api.example.com/product/123', '[{"type": "String", "arg": "username"}]', 2,
        '{"Content-Type": "application/json", "Authorization": "Bearer token456"}',
        '{"Content-Type": "application/json"}', 1),
       ('Delete Product', 'https://api.example.com/product/123', null, 3,
        '{"Content-Type": "application/json", "Authorization": "Bearer token456"}',
        '{"Content-Type": "application/json"}', 1),
       ('List Orders', 'https://api.example.com/orders', null, 0,
        '{"Content-Type": "application/json", "Authorization": "Bearer token789"}',
        '{"Content-Type": "application/json"}', 1),
       ('Create Order', 'https://api.example.com/orders', '[{"type": "String", "arg": "username"}]', 1,
        '{"Content-Type": "application/json", "Authorization": "Bearer token789"}',
        '{"Content-Type": "application/json"}', 1),
       ('Retrieve Order', 'https://api.example.com/orders/123', null, 0,
        '{"Content-Type": "application/json", "Authorization": "Bearer token789"}',
        '{"Content-Type": "application/json"}', 1),
       ('Cancel Order', 'https://api.example.com/orders/123/cancel', null, 3,
        '{"Content-Type": "application/json", "Authorization": "Bearer token789"}',
        '{"Content-Type": "application/json"}', 1),
       ('Create Payment', 'https://api.example.com/payments', '[{"type": "String", "arg": "username"}]', 1,
        '{"Content-Type": "application/json", "Authorization": "Bearer token123"}',
        '{"Content-Type": "application/json"}', 1),
       ('Get Payment Details', 'https://api.example.com/payments/456', null, 0,
        '{"Content-Type": "application/json", "Authorization": "Bearer token456"}',
        '{"Content-Type": "application/json"}', 1),
       ('Update Payment Details', 'https://api.example.com/payments/456', '[{"type": "String", "arg": "username"}]', 2,
        '{"Content-Type": "application/json", "Authorization": "Bearer token456"}',
        '{"Content-Type": "application/json"}', 1);