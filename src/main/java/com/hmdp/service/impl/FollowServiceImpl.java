package com.hmdp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.FEED_KEY;

@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {

    private final IUserService userService;
    private final BlogMapper blogMapper;
    private final StringRedisTemplate redisTemplate;

    public FollowServiceImpl(IUserService userService,
                             BlogMapper blogMapper,
                             StringRedisTemplate redisTemplate) {
        this.userService = userService;
        this.blogMapper = blogMapper;
        this.redisTemplate = redisTemplate;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result follow(Long followUserId, Boolean followed) {
        UserDTO currentUser = UserHolder.getUser();
        if (currentUser == null) {
            return Result.fail("请先登录");
        }
        if (followUserId == null || followed == null) {
            return Result.fail("关注参数不完整");
        }
        if (followUserId.equals(currentUser.getId())) {
            return Result.fail("不能关注自己");
        }
        if (userService.getById(followUserId) == null) {
            return Result.fail("目标用户不存在");
        }

        QueryWrapper<Follow> relation = new QueryWrapper<Follow>()
                .eq("user_id", currentUser.getId())
                .eq("follow_user_id", followUserId);
        if (followed) {
            if (getBaseMapper().insertIgnore(currentUser.getId(), followUserId) > 0) {
                afterCommit(() -> backfillFeed(currentUser.getId(), followUserId));
            }
            return Result.ok(true);
        }

        remove(relation);
        afterCommit(() -> removeAuthorFromFeed(currentUser.getId(), followUserId));
        return Result.ok(false);
    }

    @Override
    public Result isFollow(Long followUserId) {
        UserDTO currentUser = UserHolder.getUser();
        if (currentUser == null) {
            return Result.fail("请先登录");
        }
        return Result.ok(count(new QueryWrapper<Follow>()
                .eq("user_id", currentUser.getId())
                .eq("follow_user_id", followUserId)) > 0);
    }

    @Override
    public Result commonFollows(Long otherUserId) {
        UserDTO currentUser = UserHolder.getUser();
        if (currentUser == null) {
            return Result.fail("请先登录");
        }
        List<Follow> mine = list(new QueryWrapper<Follow>().eq("user_id", currentUser.getId()));
        if (mine.isEmpty()) {
            return Result.ok(new ArrayList<>());
        }
        Set<Long> mineIds = mine.stream().map(Follow::getFollowUserId).collect(Collectors.toSet());
        List<Long> commonIds = list(new QueryWrapper<Follow>().eq("user_id", otherUserId)).stream()
                .map(Follow::getFollowUserId)
                .filter(mineIds::contains)
                .collect(Collectors.toList());
        if (commonIds.isEmpty()) {
            return Result.ok(new ArrayList<>());
        }
        return Result.ok(userService.listByIds(commonIds).stream()
                .map(this::toDto)
                .collect(Collectors.toList()));
    }

    private void backfillFeed(Long userId, Long authorId) {
        List<Blog> recent = blogMapper.selectList(new QueryWrapper<Blog>()
                .eq("user_id", authorId)
                .orderByDesc("create_time")
                .last("LIMIT 20"));
        for (Blog blog : recent) {
            long score = blog.getCreateTime() == null
                    ? System.currentTimeMillis()
                    : blog.getCreateTime().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
            redisTemplate.opsForZSet().add(FEED_KEY + userId, blog.getId().toString(), score);
        }
    }

    private void removeAuthorFromFeed(Long userId, Long authorId) {
        List<Blog> blogs = blogMapper.selectList(new QueryWrapper<Blog>()
                .select("id")
                .eq("user_id", authorId));
        if (!blogs.isEmpty()) {
            Object[] blogIds = blogs.stream().map(blog -> blog.getId().toString()).toArray();
            redisTemplate.opsForZSet().remove(FEED_KEY + userId, blogIds);
        }
    }

    private UserDTO toDto(User user) {
        UserDTO dto = new UserDTO();
        dto.setId(user.getId());
        dto.setNickName(user.getNickName());
        dto.setIcon(user.getIcon());
        return dto;
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
