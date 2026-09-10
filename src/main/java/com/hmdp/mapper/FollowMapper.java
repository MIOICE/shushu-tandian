package com.hmdp.mapper;

import com.hmdp.entity.Follow;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;

/**
 * <p>
 *  Mapper 接口
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface FollowMapper extends BaseMapper<Follow> {

    @Insert("INSERT IGNORE INTO tb_follow(user_id, follow_user_id) VALUES(#{userId}, #{followUserId})")
    int insertIgnore(@Param("userId") Long userId, @Param("followUserId") Long followUserId);

}
