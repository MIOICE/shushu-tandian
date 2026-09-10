package com.hmdp.controller;

import com.hmdp.dto.Result;
import com.hmdp.service.ICampusService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/campus")
public class CampusController {

    private final ICampusService campusService;

    public CampusController(ICampusService campusService) {
        this.campusService = campusService;
    }

    @GetMapping
    public Result queryActive(@RequestParam(value = "city", required = false) String city) {
        return campusService.queryActive(city);
    }

    @GetMapping("/{id}")
    public Result queryById(@PathVariable("id") Long id) {
        return campusService.queryById(id);
    }
}
