package com.hmdp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hmdp.dto.Result;
import com.hmdp.entity.Blog;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IBlogService extends IService<Blog> {

    /**
     * 查询博客详情
     *
     * @param id
     * @return
     */
    Result queryBlogById(Long id);

    /**
     * 查询热门博客
     *
     * @param current
     * @return
     */
    Result queryHotBlog(Integer current);

    /**
     * 点赞博客
     *
     * @param id
     */
    void likeBlog(Long id);

    /**
     * 查询博客点赞用户列表
     *
     * @param id 博客ID
     * @return 点赞用户列表
     */
    Result queryBlogLikes(Long id);

    /**
     * 查询指定用户发布的博客列表
     *
     * @param current 页码
     * @param id      用户ID
     * @return 博客列表
     */
    Result queryBlogByUserId(Integer current, Long id);

    /**
     * 保存博客
     *
     * @param blog 博客信息
     * @return 保存结果
     */
    Result saveBlog(Blog blog);


    /**
     * 滚动查询用户关注对象的博客
     * @param lastId 最后id
     * @param offset 偏移量
     * @return
     */
    Result scrollFollow(Long lastId, Integer offset);
}
