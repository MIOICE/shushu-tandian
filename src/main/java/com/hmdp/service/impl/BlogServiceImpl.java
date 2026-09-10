package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HtmlUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.dto.ScrollResult;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.BlogLike;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogLikeMapper;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.hmdp.service.IFollowService;
import com.hmdp.service.IShopService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.FEED_KEY;

@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {

    private static final int FEED_PAGE_SIZE = 5;

    private final IUserService userService;
    private final IShopService shopService;
    private final IFollowService followService;
    private final BlogLikeMapper blogLikeMapper;
    private final StringRedisTemplate redisTemplate;

    public BlogServiceImpl(IUserService userService,
                           IShopService shopService,
                           IFollowService followService,
                           BlogLikeMapper blogLikeMapper,
                           StringRedisTemplate redisTemplate) {
        this.userService = userService;
        this.shopService = shopService;
        this.followService = followService;
        this.blogLikeMapper = blogLikeMapper;
        this.redisTemplate = redisTemplate;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result createBlog(Blog blog) {
        UserDTO currentUser = UserHolder.getUser();
        if (currentUser == null) {
            return Result.fail("请先登录");
        }
        String validationError = validateBlog(blog);
        if (validationError != null) {
            return Result.fail(validationError);
        }
        blog.setUserId(currentUser.getId())
                .setTitle(HtmlUtil.cleanHtmlTag(blog.getTitle()).trim())
                .setContent(HtmlUtil.cleanHtmlTag(blog.getContent()).trim())
                .setLiked(0)
                .setComments(0);
        save(blog);

        Long authorId = currentUser.getId();
        long createdAt = System.currentTimeMillis();
        afterCommit(() -> {
            List<Follow> followers = followService.query()
                    .eq("follow_user_id", authorId)
                    .list();
            for (Follow follower : followers) {
                redisTemplate.opsForZSet().add(
                        FEED_KEY + follower.getUserId(), blog.getId().toString(), createdAt);
            }
        });
        return Result.ok(blog.getId());
    }

    @Override
    public Result queryBlogById(Long id) {
        Blog blog = getById(id);
        return blog == null ? Result.fail("探店笔记不存在") : Result.ok(fillBlog(blog));
    }

    @Override
    public Result queryHotBlog(Integer current) {
        int pageNumber = current == null || current < 1 ? 1 : current;
        Page<Blog> page = query()
                .orderByDesc("liked")
                .orderByDesc("create_time")
                .page(new Page<>(pageNumber, SystemConstants.MAX_PAGE_SIZE));
        page.getRecords().forEach(this::fillBlog);
        return Result.ok(page.getRecords());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result likeBlog(Long id) {
        UserDTO currentUser = UserHolder.getUser();
        if (currentUser == null) {
            return Result.fail("请先登录");
        }
        if (getById(id) == null) {
            return Result.fail("探店笔记不存在");
        }
        QueryWrapper<BlogLike> relation = new QueryWrapper<BlogLike>()
                .eq("blog_id", id)
                .eq("user_id", currentUser.getId());
        boolean liked = blogLikeMapper.selectCount(relation) > 0;
        if (liked) {
            int deleted = blogLikeMapper.delete(relation);
            if (deleted > 0) {
                update().setSql("liked = IF(liked > 0, liked - 1, 0)").eq("id", id).update();
            }
            return Result.ok(false);
        }

        int inserted = blogLikeMapper.insertIgnore(id, currentUser.getId());
        if (inserted > 0) {
            update().setSql("liked = liked + 1").eq("id", id).update();
        }
        return Result.ok(true);
    }

    @Override
    public Result queryBlogLikes(Long id) {
        if (getById(id) == null) {
            return Result.fail("探店笔记不存在");
        }
        List<BlogLike> likes = blogLikeMapper.selectList(new QueryWrapper<BlogLike>()
                .eq("blog_id", id)
                .orderByDesc("create_time")
                .last("LIMIT 5"));
        if (likes.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }
        List<Long> userIds = likes.stream().map(BlogLike::getUserId).collect(Collectors.toList());
        return Result.ok(usersInOrder(userIds));
    }

    @Override
    public Result queryBlogOfFollow(Long max, Integer offset) {
        UserDTO currentUser = UserHolder.getUser();
        if (currentUser == null) {
            return Result.fail("请先登录");
        }
        long maxScore = max == null ? System.currentTimeMillis() : max;
        int pageOffset = offset == null || offset < 0 ? 0 : offset;
        Set<ZSetOperations.TypedTuple<String>> tuples = redisTemplate.opsForZSet()
                .reverseRangeByScoreWithScores(
                        FEED_KEY + currentUser.getId(), 0, maxScore, pageOffset, FEED_PAGE_SIZE);
        if (tuples == null || tuples.isEmpty()) {
            ScrollResult empty = new ScrollResult();
            empty.setList(Collections.emptyList());
            empty.setMinTime(maxScore);
            empty.setOffset(0);
            return Result.ok(empty);
        }

        List<Long> blogIds = new ArrayList<>(tuples.size());
        long minTime = 0L;
        int sameScoreOffset = 1;
        for (ZSetOperations.TypedTuple<String> tuple : tuples) {
            if (tuple.getValue() == null || tuple.getScore() == null) {
                continue;
            }
            blogIds.add(Long.valueOf(tuple.getValue()));
            long score = tuple.getScore().longValue();
            if (score == minTime) {
                sameScoreOffset++;
            } else {
                minTime = score;
                sameScoreOffset = 1;
            }
        }
        if (blogIds.isEmpty()) {
            return Result.ok(new ScrollResult());
        }
        Map<Long, Blog> byId = listByIds(blogIds).stream()
                .collect(Collectors.toMap(Blog::getId, blog -> blog));
        List<Blog> ordered = blogIds.stream()
                .map(byId::get)
                .filter(blog -> blog != null)
                .map(this::fillBlog)
                .collect(Collectors.toList());

        ScrollResult result = new ScrollResult();
        result.setList(ordered);
        result.setMinTime(minTime);
        result.setOffset(sameScoreOffset);
        return Result.ok(result);
    }

    private Blog fillBlog(Blog blog) {
        User author = userService.getById(blog.getUserId());
        if (author != null) {
            blog.setName(author.getNickName());
            blog.setIcon(author.getIcon());
        }
        UserDTO currentUser = UserHolder.getUser();
        boolean liked = currentUser != null && blogLikeMapper.selectCount(new QueryWrapper<BlogLike>()
                .eq("blog_id", blog.getId())
                .eq("user_id", currentUser.getId())) > 0;
        blog.setIsLike(liked);
        return blog;
    }

    private List<UserDTO> usersInOrder(List<Long> userIds) {
        Map<Long, User> byId = new HashMap<>();
        for (User user : userService.listByIds(userIds)) {
            byId.put(user.getId(), user);
        }
        return userIds.stream()
                .map(byId::get)
                .filter(user -> user != null)
                .map(user -> {
                    UserDTO dto = new UserDTO();
                    dto.setId(user.getId());
                    dto.setNickName(user.getNickName());
                    dto.setIcon(user.getIcon());
                    return dto;
                })
                .collect(Collectors.toList());
    }

    private String validateBlog(Blog blog) {
        if (blog == null || blog.getShopId() == null || shopService.getById(blog.getShopId()) == null) {
            return "关联店铺不存在";
        }
        if (StrUtil.isBlank(blog.getTitle()) || blog.getTitle().length() > 255) {
            return "标题不能为空且不能超过 255 个字符";
        }
        if (StrUtil.isBlank(blog.getContent()) || blog.getContent().length() > 2048) {
            return "内容不能为空且不能超过 2048 个字符";
        }
        if (StrUtil.isBlank(blog.getImages()) || blog.getImages().split(",").length > 9) {
            return "探店图片不能为空且最多 9 张";
        }
        return null;
    }

    private void afterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }
}
