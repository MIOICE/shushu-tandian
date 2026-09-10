package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

import java.lang.reflect.Type;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Override
    public Result queryTypeList() {
        //1.拼接缓存key
        String cacheKey = TYPE_LIST_KEY;
        //1.从redis查询商铺缓存
        String listJson = stringRedisTemplate.opsForValue().get(cacheKey);
        //2.判断是否存在
        if (StrUtil.isNotBlank(listJson)) {
            //3.存在，直接返回
            List<ShopType> typeList = JSONUtil.toList(listJson, ShopType.class);
            return Result.ok(typeList);
        }

        //4.不存在，查询数据库
        List<ShopType> typeList = list();
        //5.不存在，返回错误
        if (typeList == null || typeList.isEmpty()) {
            // 缓存空字符串，有效期短一点，防止缓存穿透
            stringRedisTemplate.opsForValue().set(cacheKey, "", 2L, TimeUnit.MINUTES);
            // 数据库也没数据 → 返回错误
            return Result.fail("店铺分类不存在");
        }
        //6.存在，写入redis
        stringRedisTemplate.opsForValue().set(cacheKey, JSONUtil.toJsonStr(typeList),TYPE_LIST_TTL, TimeUnit.MINUTES);
        //7.返回数据
        return Result.ok(typeList);
    }
}
