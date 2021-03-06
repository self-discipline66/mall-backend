package com.mall.service;

import com.mall.model.domain.User;
import com.baomidou.mybatisplus.extension.service.IService;
import com.mall.model.domain.UserDTO;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

/**
 * @author huawei
 * @description 针对表【user】的数据库操作Service
 * @createDate 2022-03-20 10:48:34
 */
public interface UserService extends IService<User> {
    /**
     * 用户注册
     *
     * @param username      用户名
     * @param password      密码
     * @param checkPassword 校验密码
     * @return
     */
    Integer userRegister(String username, String password, String checkPassword);

    /**
     * 用户登录
     *
     * @param username 用户名
     * @param password 密码
     * @return
     */
    String userLogin(String username, String password);

    /**
     * 查询用户是否存在
     *
     * @param username 用户名
     * @return
     */
    Boolean checkUsername(String username);

    /**
     * 获取所有用户
     * @return
     */
    List<UserDTO> getAllUsers();

    /**
     * 禁用某个用户
     *
     * @param userId 用户id
     * @return
     */
    Boolean disableUser(Integer userId);

    /**
     * 永久删除该账户及有关信息
     *
     * @param userId 用户id
     * @return
     */
    Boolean deleteUser(Integer userId);
}
