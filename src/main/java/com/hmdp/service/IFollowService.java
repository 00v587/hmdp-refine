package com.hmdp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hmdp.dto.Result;
import com.hmdp.entity.Follow;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IFollowService extends IService<Follow> {

    /**
     * 关注或取关
     * @param followUserId 关联的用户id
     * @param isFollow 是否关注
     * @return
     */
    Result follow(Long followUserId, Boolean isFollow);

    /**
     * 查询是否关注
     * @param followUserId 关联的用户id
     * @return
     */
    Result isFollow(Long followUserId);

    /**
     * 查看共同关注
     * @param followUserId 关联的用户id
     * @return
     */
    Result commonFollow(Long followUserId);
}
