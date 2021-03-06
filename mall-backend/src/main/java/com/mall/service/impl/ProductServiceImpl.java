package com.mall.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.convert.Convert;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mall.exception.BusinessException;
import com.mall.model.domain.Product;
import com.mall.mapper.ProductMapper;
import com.mall.model.domain.ProductPicture;
import com.mall.service.CategoryService;
import com.mall.service.ProductPictureService;
import com.mall.service.ProductService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;

import javax.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;

import static com.mall.base.ErrorCode.*;
import static com.mall.constant.MessageConstant.*;
import static com.mall.constant.RedisConstant.*;

/**
 * @author sgh
 */
@Service
public class ProductServiceImpl extends ServiceImpl<ProductMapper, Product>
        implements ProductService {

    @Resource
    private ProductMapper productMapper;

    @Resource
    private ProductPictureService productPictureService;

    @Resource
    private CategoryService categoryService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public List<Product> getProductByCategoryName(String categoryName) {
        //根据分类名获取分类号
        Integer categoryId = categoryService.getIdByName(categoryName);
        //根据分类号获取商品
        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Product::getCategoryId, categoryId).eq(Product::getIsAllowance, 0)
                .orderByDesc(Product::getProductSales).last("LIMIT 7");
        return productMapper.selectList(wrapper);
    }

    @Override
    public List<Product> getHotProduct(String[] categoryNames) {
        String key = Arrays.asList(categoryNames).contains("电视机") ? APPLIANCE_CATEGORY_KEY : ACCESSORY_CATEGORY_KEY;
        //根据分类名获取redis中销量前7
        Set<String> range = stringRedisTemplate.opsForZSet().reverseRange(key, 0, 6);
        if (range != null) {
            System.out.println("range in redis = " + range);
            List<Product> collect = range.stream().map(jsonStr -> JSONUtil.toBean(jsonStr, Product.class)).collect(Collectors.toList());
            if (!collect.isEmpty()) {
                return collect;
            }
        }
        //根据分类名获取分类号
        Integer[] categoryIds = new Integer[categoryNames.length];
        for (int i = 0; i < categoryNames.length; i++) {
            Integer categoryId = categoryService.getIdByName(categoryNames[i]);
            categoryIds[i] = categoryId;
        }
        //根据分类号获取销量前7名
        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(Product::getCategoryId, Arrays.asList(categoryIds)).eq(Product::getIsAllowance, 0)//分类号查询
                .orderByDesc(Product::getProductSales).last("LIMIT 7");
        List<Product> products = productMapper.selectList(wrapper);
        if (products == null) {
            throw new BusinessException(GET_MESSAGE_ERROR, GET_HOT_PRODUCT_ERROR);
        } else {
            return products;
        }
    }

    @Override
    public IPage<Product> getALlProduct(int currentPage, int pageSize) {
        IPage<Product> page = new Page<>(currentPage, pageSize);
        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<>();
        //根据id降序查询让最新的产品在前面
        wrapper.orderByDesc(Product::getProductId).eq(Product::getIsAllowance, 0);
        return productMapper.selectPage(page, wrapper);
    }

    @Override
    public Boolean orderProduct(Integer num, Integer productId) {
        //redis判断库存
        Integer max = this.getMaxPurchasableNum(productId);
        if (num > max) {
            throw new BusinessException(REQUEST_SERVICE_ERROR, INVENTORY_SHORTAGE_ERROR);
        }
        //库存充足
//        Product product = this.getById(productId);
//        product.setProductSales(product.getProductSales() + num);
//        Product newProduct = new Product();
//        BeanUtil.copyProperties(product,newProduct,false);
//        boolean success = updateById(newProduct);
//        if (success) {
            //修改缓存库存
            String key = INVENTORY_KEY + ":" + productId;
            stringRedisTemplate.opsForValue().set(key, String.valueOf(max - num));
            return Boolean.TRUE;
//        }else{
//            throw new BusinessException(REQUEST_SERVICE_ERROR, ORDER_FAIL);
//        }
    }

    @Override
    public IPage<Product> getProductByCategory(Integer categoryId, int currentPage, int pageSize) {
        IPage<Product> page = new Page<>(currentPage, pageSize);
        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<>();
        //根据id降序查询让最新的产品在前面
        wrapper.eq(Product::getCategoryId, categoryId).eq(Product::getIsAllowance, 0)
                .orderByDesc(Product::getProductId);
        return productMapper.selectPage(page, wrapper);
    }

    @Override
    public IPage<Product> getProductBySearch(String queryString, int currentPage, int pageSize) {
        IPage<Product> page = new Page<>(currentPage, pageSize);
        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Product::getIsAllowance, 0)
                .like(Product::getProductName, queryString)
                .or().like(Product::getProductTitle, queryString)
                .or().like(Product::getProductIntro, queryString);
        return productMapper.selectPage(page, wrapper);
    }

    @Override
    public Integer addProduct(Product product) {
        //对数值进行校验
        String productName = product.getProductName();
        String productTitle = product.getProductTitle();
        Double productPrice = product.getProductPrice();
        Integer categoryId = product.getCategoryId();
        Double productSellingPrice = product.getProductSellingPrice();
        Integer productNum = product.getProductNum();
        String productPicture = product.getProductPicture();
        if (StringUtils.isAnyBlank(productName, productPicture, productTitle) || productNum == null || categoryId == null || productPrice == null || productSellingPrice == null) {
            throw new BusinessException(PARAMS_PATTERN_ERROR, PRODUCT_INFORMATION_ERROR);
        }
        //封装
        Product newProduct = new Product();
        newProduct.setProductName(productName);
        newProduct.setCategoryId(categoryId);
        newProduct.setProductTitle(productTitle);
        newProduct.setProductIntro(product.getProductIntro());
        newProduct.setProductPicture(productPicture);
        newProduct.setProductPrice(productPrice);
        newProduct.setProductSellingPrice(productSellingPrice);
        newProduct.setProductNum(productNum);
        newProduct.setProductSales(0);
        boolean success = this.save(newProduct);
        if (!success) {
            throw new BusinessException(REQUEST_SERVICE_ERROR, ADD_FAIL);
        }
        //写入库存缓存
        String key = INVENTORY_KEY + ":" + product.getProductId();
        stringRedisTemplate.opsForValue().set(key, String.valueOf(product.getProductNum()));
        return newProduct.getProductId();
    }

    @Override
    @Transactional
    public Boolean addProductPictures(String[] pictures, Integer productId, String pictureIntro) {
        if (pictures.length == 0) {
            throw new BusinessException(PARAMS_NULL_ERROR);
        }
        //判断商品是否存在
        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Product::getProductId, productId);
        long count = count(wrapper);
        if (count == 0) {
            throw new BusinessException(GET_MESSAGE_ERROR, PRODUCT_NOT_EXIST);
        }
        //遍历添加
        ProductPicture productPicture;
        for (String picture : pictures) {
            productPicture = new ProductPicture();
            productPicture.setProductId(productId);
            productPicture.setProductPictureUrl(picture);
            productPicture.setIntro(pictureIntro);
            productPictureService.save(productPicture);
        }
        return Boolean.TRUE;
    }

    @Override
    @Transactional
    public Boolean deleteProduct(Integer productId) {
        //查询是否存在
        Product product = getById(productId);
        if (product == null) {
            throw new BusinessException(GET_MESSAGE_ERROR, PRODUCT_NOT_EXIST);
        }
        //删除商品及对应的图片
        boolean success = removeById(productId);
        LambdaQueryWrapper<ProductPicture> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ProductPicture::getProductId, productId);
        productPictureService.remove(wrapper);
        //检查是否是热门商品
        Integer categoryId = product.getCategoryId();
        if (categoryId != null) {
            //是热门商品就删除缓存
            if (categoryId > 1 && categoryId < 5) {
                stringRedisTemplate.opsForZSet().remove(APPLIANCE_CATEGORY_KEY, JSONUtil.toJsonStr(product));
            } else if (categoryId > 4) {
                stringRedisTemplate.opsForZSet().remove(ACCESSORY_CATEGORY_KEY, JSONUtil.toJsonStr(product));
            }
        }
        if (!success) {
            throw new BusinessException(REQUEST_SERVICE_ERROR, DELETE_FAIL);
        }
        return Boolean.TRUE;
    }

    @Override
    public Boolean removeProductPictures(String[] pictures, Integer productId) {
        if (pictures.length == 0) {
            throw new BusinessException(PARAMS_NULL_ERROR);
        }
        //查询是否存在
        Product product = getById(productId);
        if (product == null) {
            throw new BusinessException(GET_MESSAGE_ERROR, PRODUCT_NOT_EXIST);
        }
        //删除指定图片
        LambdaQueryWrapper<ProductPicture> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ProductPicture::getProductId, productId).in(ProductPicture::getProductPictureUrl, Arrays.asList(pictures));
        boolean success = productPictureService.remove(wrapper);
        if (!success) {
            throw new BusinessException(REQUEST_SERVICE_ERROR, DELETE_FAIL);
        }
        return Boolean.TRUE;
    }

    @Override
    @Transactional
    public Boolean updateProduct(Product product) {
        //查询是否存在
        Integer productId = product.getProductId();
        Product queryResult = getById(productId);
        if (queryResult == null) {
            throw new BusinessException(GET_MESSAGE_ERROR, PRODUCT_NOT_EXIST);
        }
        //进行更新
        // 1.更新数据库
        product.setVersion(queryResult.getVersion());
        boolean success = updateById(product);
        if (!success) {
            throw new BusinessException(REQUEST_SERVICE_ERROR, UPDATE_FAIL);
        }
        //更新库存缓存
        String key = INVENTORY_KEY + ":" + productId;
        int max = Integer.parseInt(Objects.requireNonNull(stringRedisTemplate.opsForValue().get(key)));
        int newNum = product.getProductNum() - queryResult.getProductNum();
        if ((max + newNum) < 0) {
            //主动削减库存至不足
            stringRedisTemplate.opsForValue().set(key, "0");
        }
        stringRedisTemplate.opsForValue().set(key, String.valueOf(max + newNum));
        Product updatedProduct = getById(productId);
        //2.检查是否是热门商品
        Integer categoryId = updatedProduct.getCategoryId();
        if (categoryId != null) {
            //是热门分类商品就重写缓存
            if (categoryId > 1 && categoryId < 5) {
                stringRedisTemplate.opsForZSet().remove(APPLIANCE_CATEGORY_KEY, JSONUtil.toJsonStr(queryResult));
                stringRedisTemplate.opsForZSet().add(APPLIANCE_CATEGORY_KEY,
                        JSONUtil.toJsonStr(updatedProduct), Convert.convert(Double.class, updatedProduct.getProductSales()));
            } else if (categoryId > 4) {
                stringRedisTemplate.opsForZSet().remove(ACCESSORY_CATEGORY_KEY, JSONUtil.toJsonStr(queryResult));
                stringRedisTemplate.opsForZSet().add(ACCESSORY_CATEGORY_KEY,
                        JSONUtil.toJsonStr(updatedProduct), Convert.convert(Double.class, updatedProduct.getProductSales()));
            }
        }
        return Boolean.TRUE;
    }

    @Override
    public List<Product> getProductByCategoryIds(Integer[] categoryList) {
        if (categoryList == null) {
            return Collections.emptyList();
        }
        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(Product::getCategoryId, Arrays.asList(categoryList)).eq(Product::getIsAllowance, 0);
        return list(wrapper);
    }

    @Override
    public Integer getMaxPurchasableNum(Integer productId) {
        //查缓存
        String key = INVENTORY_KEY + ":" + productId;
        String maxPurchasableNum = stringRedisTemplate.opsForValue().get(key);
        if (maxPurchasableNum != null) {
            return Integer.valueOf(maxPurchasableNum);
        }
        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Product::getProductId, productId).eq(Product::getIsAllowance, 0);
        Product product = this.getOne(wrapper);
        int max = product.getProductNum() - product.getProductSales();
        if (max > 0) {
            stringRedisTemplate.opsForValue().set(key, String.valueOf(max));
        }
        return Math.max(max, 0);
    }
}




