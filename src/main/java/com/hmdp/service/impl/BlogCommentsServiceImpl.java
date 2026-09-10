package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HtmlUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.BlogComments;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogCommentsMapper;
import com.hmdp.service.IBlogCommentsService;
import com.hmdp.service.IBlogService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.UserHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BlogCommentsServiceImpl extends ServiceImpl<BlogCommentsMapper, BlogComments>
        implements IBlogCommentsService {

    private final IBlogService blogService;
    private final IUserService userService;

    public BlogCommentsServiceImpl(IBlogService blogService, IUserService userService) {
        this.blogService = blogService;
        this.userService = userService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result createComment(BlogComments comment) {
        UserDTO currentUser = UserHolder.getUser();
        if (currentUser == null) {
            return Result.fail("请先登录");
        }
        if (comment == null || comment.getBlogId() == null || blogService.getById(comment.getBlogId()) == null) {
            return Result.fail("探店笔记不存在");
        }
        if (StrUtil.isBlank(comment.getContent()) || comment.getContent().length() > 255) {
            return Result.fail("评论不能为空且不能超过 255 个字符");
        }

        long answerId = comment.getAnswerId() == null ? 0L : comment.getAnswerId();
        if (answerId > 0) {
            BlogComments answer = getById(answerId);
            if (answer == null || !comment.getBlogId().equals(answer.getBlogId())
                    || !Integer.valueOf(0).equals(answer.getStatus())) {
                return Result.fail("被回复的评论不存在");
            }
            comment.setParentId(answer.getParentId() == null || answer.getParentId() == 0
                    ? answer.getId() : answer.getParentId());
            comment.setAnswerId(answer.getId());
        } else {
            comment.setParentId(0L);
            comment.setAnswerId(0L);
        }

        comment.setUserId(currentUser.getId())
                .setContent(HtmlUtil.cleanHtmlTag(comment.getContent()).trim())
                .setLiked(0)
                .setStatus(0);
        save(comment);
        blogService.update()
                .setSql("comments = IFNULL(comments, 0) + 1")
                .eq("id", comment.getBlogId())
                .update();
        return Result.ok(comment.getId());
    }

    @Override
    public Result queryComments(Long blogId, Integer current) {
        if (blogService.getById(blogId) == null) {
            return Result.fail("探店笔记不存在");
        }
        int pageNumber = current == null || current < 1 ? 1 : current;
        Page<BlogComments> page = query()
                .eq("blog_id", blogId)
                .eq("status", 0)
                .orderByDesc("create_time")
                .page(new Page<>(pageNumber, 20));
        page.getRecords().forEach(this::fillAuthor);
        return Result.ok(page.getRecords(), page.getTotal());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result deleteComment(Long id) {
        UserDTO currentUser = UserHolder.getUser();
        if (currentUser == null) {
            return Result.fail("请先登录");
        }
        BlogComments comment = getById(id);
        if (comment == null || !currentUser.getId().equals(comment.getUserId())
                || !Integer.valueOf(0).equals(comment.getStatus())) {
            return Result.fail("评论不存在或无权删除");
        }
        boolean hidden = update()
                .set("status", 2)
                .eq("id", id)
                .eq("user_id", currentUser.getId())
                .eq("status", 0)
                .update();
        if (!hidden) {
            return Result.fail("评论已经删除");
        }
        blogService.update()
                .setSql("comments = IF(comments > 0, comments - 1, 0)")
                .eq("id", comment.getBlogId())
                .update();
        return Result.ok();
    }

    private void fillAuthor(BlogComments comment) {
        User user = userService.getById(comment.getUserId());
        if (user != null) {
            comment.setUserName(user.getNickName());
            comment.setUserIcon(user.getIcon());
        }
    }
}
