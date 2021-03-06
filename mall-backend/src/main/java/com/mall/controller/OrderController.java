package com.mall.controller;

import com.mall.base.BaseResponse;
import com.mall.base.ResultUtils;
import com.mall.constant.MessageConstant;
import com.mall.exception.BusinessException;
import com.mall.model.domain.Orders;
import com.mall.model.domain.ShoppingCart;
import com.mall.model.domain.UserDTO;
import com.mall.model.request.OrderRequest;
import com.mall.service.OrdersService;
import com.mall.utils.UserHolder;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;

import static com.mall.base.ErrorCode.*;
import static com.mall.constant.MessageConstant.*;

@RestController
@RequestMapping("/order")
public class OrderController {

    @Resource
    private OrdersService ordersService;

    /**
     * 获取该用户的所有订单信息
     *
     * @param orderRequest 订单请求体
     * @return
     */
    @PostMapping("/getOrder")
    public BaseResponse<List<List<Orders>>> getOrder(@RequestBody OrderRequest orderRequest) {
        if (orderRequest == null) {
            throw new BusinessException(PARAMS_NULL_ERROR);
        }
        String userId = orderRequest.getUserId();
        if (StringUtils.isAnyBlank(userId)) {
            throw new BusinessException(PARAMS_NULL_ERROR);
        }
        List<List<Orders>> orders = ordersService.getOrders(userId);
        return ResultUtils.success(orders, SELECT_SUCCESS);
    }

    /**
     * 添加订单
     *
     * @param orderRequest 订单请求体
     * @return
     */
    @PostMapping("/addOrder")
    public BaseResponse<Boolean> addOrders(@RequestBody OrderRequest orderRequest) {
        if (orderRequest == null) {
            throw new BusinessException(PARAMS_NULL_ERROR);
        }
        UserDTO user = UserHolder.getUser();
        Integer userId = user.getUserId();
        ShoppingCart[] shoppingCart = orderRequest.getShoppingCart();
        if (userId == null || shoppingCart == null || shoppingCart.length == 0) {
            throw new BusinessException(PARAMS_NULL_ERROR);
        }
        Boolean result = ordersService.addOrders(String.valueOf(userId), shoppingCart);
        if (Boolean.TRUE.equals(result)) {
            return ResultUtils.success(true, ORDER_SUCCESS);
        } else {
            throw new BusinessException(REQUEST_SERVICE_ERROR, ORDER_FAIL);
        }
    }

    /**
     * 获取所有订单
     *
     * @return
     */
    @GetMapping()
    public BaseResponse<List<List<Orders>>> getAllOrders() {
        List<List<Orders>> result = ordersService.getAllOrders();
        return ResultUtils.success(result, SELECT_SUCCESS);
    }

}
