package com.hmdp.controller;

import com.hmdp.dto.Result;
import com.hmdp.risk.RiskLimit;
import com.hmdp.service.IFollowService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/follow")
public class FollowController {

    private final IFollowService followService;

    public FollowController(IFollowService followService) {
        this.followService = followService;
    }

    @PutMapping("/{id}/{followed}")
    @RiskLimit(userLimit = 60, ipLimit = 180, deviceLimit = 90, windowSeconds = 60)
    public Result follow(@PathVariable("id") Long followUserId,
                         @PathVariable("followed") Boolean followed) {
        return followService.follow(followUserId, followed);
    }

    @GetMapping("/or/not/{id}")
    public Result isFollow(@PathVariable("id") Long followUserId) {
        return followService.isFollow(followUserId);
    }

    @GetMapping("/common/{id}")
    public Result commonFollows(@PathVariable("id") Long otherUserId) {
        return followService.commonFollows(otherUserId);
    }
}
