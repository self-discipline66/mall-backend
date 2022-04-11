# mall-backend
商城后台系统 基于springboot + mybatis + mybatisplus + redis 

添加了redis实现对热门商品的缓存以及用户登录token存储，同时增加了校验登录和刷新token拦截器

添加了定时任务，每天定时刷新热门商品

添加了自定义异常和错误码枚举类，便于与前端和用户信息交互，同时记录日志

添加了全局异常处理器

添加了乐观锁，解决超卖问题

添加了库存缓存，缓解数据库请求压力


