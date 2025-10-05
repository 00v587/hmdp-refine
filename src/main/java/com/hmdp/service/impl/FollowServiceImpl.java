package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Follow;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private IUserService userService;

    /**
     * 关注或取关
     * @param followUserId 关联的用户id
     * @param isFollow 是否关注
     * @return
     */
    @Override
    public Result follow(Long followUserId, Boolean isFollow) {
        // 1. 获取当前登录用户
        Long userId = UserHolder.getUser().getId();
        // 2. 判断是否关注
        if (isFollow) {
            // 2.1 关注，插入数据
            Follow follow = new Follow();
            follow.setUserId(userId);
            follow.setFollowUserId(followUserId);
            // 将关注信息存入redis
            stringRedisTemplate.opsForSet().add("follows:" + userId, followUserId.toString());
            boolean isSuccess = save(follow);
            if (!isSuccess) {
                return Result.fail("关注失败");
            }
        } else {
            // 2.2 取关，删除数据
            // 将取关信息从redis中移除
            stringRedisTemplate.opsForSet().remove("follows:" + userId, followUserId.toString());
            boolean isSuccess = remove(new QueryWrapper<Follow>().eq("user_id", userId).eq("follow_user_id", followUserId));
            if (!isSuccess) {
                return Result.fail("取关失败");
            }
        }
        return Result.ok();
    }

    /**
     * 查询是否关注
     * @param followUserId 关联的用户id
     * @return
     */
    @Override
     public Result isFollow(Long followUserId) {
        // 1. 获取当前登录用户
        Long userId = UserHolder.getUser().getId();
        // 2. 查询是否关注
        Integer count = Math.toIntExact(query().eq("user_id", userId).eq("follow_user_id", followUserId).count());
        // 3. 返回结果
        return Result.ok(count > 0);
    }

    /**
     * 查看共同关注
     * @param
     * @return
     */
    @Override
    public Result commonFollow(Long id) {
        // 1. 获取当前登录用户
        Long userId = UserHolder.getUser().getId();
        // 2. 查询共同关注
        Set<String> intersect = stringRedisTemplate.opsForSet().intersect("follows:" + userId, "follows:" + id);
        // 3. 返回结果
        if(intersect == null || intersect.isEmpty()){
            return Result.ok(Collections.EMPTY_LIST);
        }
        List<Long> ids = intersect.stream().map(Long::valueOf).collect(Collectors.toList());
        List<UserDTO> users = userService.listByIds(ids)
                .stream().map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());
        return Result.ok(users);
    }
}
