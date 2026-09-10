package com.hmdp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hmdp.dto.Result;
import com.hmdp.entity.Campus;

public interface ICampusService extends IService<Campus> {

    Result queryActive(String city);

    Result queryById(Long id);
}
