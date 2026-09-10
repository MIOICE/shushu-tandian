package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.Campus;
import com.hmdp.mapper.CampusMapper;
import com.hmdp.service.ICampusService;
import org.springframework.stereotype.Service;

@Service
public class CampusServiceImpl extends ServiceImpl<CampusMapper, Campus> implements ICampusService {

    @Override
    public Result queryActive(String city) {
        return Result.ok(query()
                .eq("status", 1)
                .eq(StrUtil.isNotBlank(city), "city", city)
                .orderByAsc("city", "name")
                .list());
    }

    @Override
    public Result queryById(Long id) {
        Campus campus = getById(id);
        if (campus == null || !Integer.valueOf(1).equals(campus.getStatus())) {
            return Result.fail("校区不存在或已停用");
        }
        return Result.ok(campus);
    }
}
