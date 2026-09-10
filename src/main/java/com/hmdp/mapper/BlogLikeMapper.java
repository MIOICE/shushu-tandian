package com.hmdp.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hmdp.entity.BlogLike;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;

public interface BlogLikeMapper extends BaseMapper<BlogLike> {

    @Insert("INSERT IGNORE INTO tb_blog_like(blog_id, user_id) VALUES(#{blogId}, #{userId})")
    int insertIgnore(@Param("blogId") Long blogId, @Param("userId") Long userId);
}
