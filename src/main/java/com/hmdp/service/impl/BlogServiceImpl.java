package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.dto.ScrollResult;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.hmdp.service.IFollowService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.BLOG_LIKED_KEY;
import static com.hmdp.utils.RedisConstants.FEED_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {

    @Autowired
    private IUserService userService;
    @Autowired
    private IFollowService followService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 发表博客
     *
     * @param
     * @return
     */
    @Override
    public Result queryHotBlog(Integer current) {
        // 根据用户查询
        Page<Blog> page = query() // 直接使用 this.query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 查询用户
        records.forEach(this::queryByUserId);
        return Result.ok(records);
    }

    /**
     * 查询博客详情
     *
     * @param id
     * @return
     */
    @Override
    public Result queryBlogById(Long id) {
        // 查询blog
        Blog blog = getById(id); // 直接使用 this.getById()
        if (blog == null) {
            return Result.fail("博客不存在");
        }
        // 查询用户
        queryByUserId(blog);
        // 查询当前用户是否点赞
        isBlogLiked(blog);
        // 返回
        return Result.ok(blog);
    }

    private void queryByUserId(Blog blog) {
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
    }

    /**
     * 点赞博客
     *
     * @param id
     */
    @Override
    public void likeBlog(Long id) {
        // 1. 查询用户是否已经点赞
        // 2. 点过赞，取消点赞, 数据库like数-1并将用户从redis中移除
        Double isMember = stringRedisTemplate.opsForZSet().score(BLOG_LIKED_KEY + id, UserHolder.getUser().getId().toString());
        if (isMember != null && isMember > 0) {
            // 取消点赞
            stringRedisTemplate.opsForZSet().remove(BLOG_LIKED_KEY + id, UserHolder.getUser().getId().toString());
            // 数据库like数-1
            update().setSql("liked = liked - 1").eq("id", id).update();
            return;
        }
        // 3. 未点赞，点赞
        // 将点赞信息存入redis的set集合中  blog : ids
        stringRedisTemplate.opsForZSet().add(BLOG_LIKED_KEY + id, UserHolder.getUser().getId().toString(), System.currentTimeMillis());
        // 线程池异步更新数据库
        asyncUpdateBlogLiked(id);
    }

    /**
     * 异步更新数据库
     *
     * @param id
     */
    private void asyncUpdateBlogLiked(Long id) {
        // 从redis中查询点赞数量
        Long count = stringRedisTemplate.opsForZSet().zCard(BLOG_LIKED_KEY + id);
        // 更新数据库
        update().setSql("liked = " + count).eq("id", id).update();
    }

    /**
     * 检查当前用户是否点赞博客
     *
     * @param blog
     */
    private void isBlogLiked(Blog blog) {
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return;
        }
        Double isMember = stringRedisTemplate.opsForZSet().score(BLOG_LIKED_KEY + blog.getId(), user.getId().toString());
        blog.setIsLike(isMember != null && isMember > 0);
    }

    /**
     * 查询博客点赞用户列表
     *
     * @param id 博客ID
     * @return 点赞用户列表
     */
    @Override
    public Result queryBlogLikes(Long id) {
        // 从Redis中获取点赞该博客的用户ID列表
        Set<String> userIds = stringRedisTemplate.opsForZSet().range(BLOG_LIKED_KEY + id, 0, 4);
        if (userIds == null || userIds.isEmpty()) {
            return Result.ok();
        }

        // 根据用户ID查询用户信息
        List<User> users = userIds.stream().map(userId -> {
            return userService.getById(Long.valueOf(userId));
        }).collect(Collectors.toList());

        return Result.ok(users);
    }

    /**
     * 查询指定id的博客
     */
    @Override
    public Result queryBlogByUserId(Integer current, Long id) {
        // 分页查询博客
        Page<Blog> page = query() // 直接使用 this.query()
                .eq("user_id", id).page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 查询用户
        records.forEach(this::queryByUserId);
        return Result.ok(records, page.getTotal());
    }

    /**
     * 删除博客
     *
     * @param
     * @return 删除结果
     */
    @Override
    public Result saveBlog(Blog blog) {
        // 1. 获取登录用户
        UserDTO user = UserHolder.getUser();
        blog.setUserId(user.getId());
        // 2. 保存探店博文
        boolean isSuccess = saveOrUpdate(blog); // 直接使用 this.saveOrUpdate()
        if (!isSuccess) {
            return Result.fail("保存失败");
        }
        // 3. 查询笔记作者的所有粉丝
        List<Follow> follows = followService.query().eq("follow_user_id", user.getId()).list();
        // 4. 把博文发送给粉丝
        for (Follow follow : follows) {
            String key = FEED_KEY + follow.getUserId();
            stringRedisTemplate.opsForZSet().add(key, blog.getId().toString(), System.currentTimeMillis());
        }
        // 5. 返回id
        return Result.ok(blog.getId());
    }


    /**
     * 滚动查询用户关注对象的博客
     * @param lastId 最后id
     * @param offset 偏移量
     * @return
     */
    @Override
    public Result scrollFollow(Long lastId, Integer offset) {
        // 1. 获取当前用户
        UserDTO user = UserHolder.getUser();
        // 2. 去redis找到收件箱
        // 3. 解析收件箱中的博客id
        Set<ZSetOperations.TypedTuple<String>> typedTuples = stringRedisTemplate.opsForZSet()
                .reverseRangeByScoreWithScores(FEED_KEY + user.getId(), 0 , lastId, offset,3);
        if (typedTuples == null || typedTuples.isEmpty()) {
            return Result.ok("没有更多了");
        }

        List<Long> ids = new ArrayList<>(typedTuples.size());
        //找到最小时间（score）
        Long minScore = 0L;
        int of = 0 ;
        for(ZSetOperations.TypedTuple<String> tuple : typedTuples){
            // 用LIst保存获取到的id和score
            ids.add(Long.valueOf(tuple.getValue()));
            Long score = tuple.getScore().longValue();
            if(score.equals(minScore)){
                of++;
            }else{
                minScore = score;
                of = 1;
            }
        }
        // 根据id查询blog 并按升序排序
        List<Blog> blogs = query()  // 使用this.query()替代blogService.query()
                .in("id", ids)
                .orderByAsc("create_time")
                .list();

        // 6. 封装返回结果
        ScrollResult scrollResult = new ScrollResult();
        scrollResult.setList(blogs);
        scrollResult.setMinTime(minScore);
        scrollResult.setOffset(of);
        return Result.ok(scrollResult);
    }
}
