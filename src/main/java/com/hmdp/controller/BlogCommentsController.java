package com.hmdp.controller;

import com.hmdp.dto.Result;
import com.hmdp.entity.BlogComments;
import com.hmdp.risk.RiskLimit;
import com.hmdp.service.IBlogCommentsService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/blog-comments")
public class BlogCommentsController {

    private final IBlogCommentsService commentsService;

    public BlogCommentsController(IBlogCommentsService commentsService) {
        this.commentsService = commentsService;
    }

    @PostMapping
    @RiskLimit(userLimit = 30, ipLimit = 120, deviceLimit = 60, windowSeconds = 60)
    public Result createComment(@RequestBody BlogComments comment) {
        return commentsService.createComment(comment);
    }

    @GetMapping("/blog/{blogId}")
    public Result queryComments(
            @PathVariable("blogId") Long blogId,
            @RequestParam(value = "current", defaultValue = "1") Integer current) {
        return commentsService.queryComments(blogId, current);
    }

    @DeleteMapping("/{id}")
    public Result deleteComment(@PathVariable("id") Long id) {
        return commentsService.deleteComment(id);
    }
}
