package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IFollowService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.*;

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
    @Resource
    private IUserService userService;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private IFollowService followService;
    private void queryBlogByUser(Blog blog) {
        Long userId = blog.getUserId();             // 获取编号
        User user = userService.getById(userId);    // 找到对象
        blog.setName(user.getNickName());           // 修改属性
        blog.setIcon(user.getIcon());
    }

    private void isBlogLiked(Blog blog){
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return;   // 先判断 user 是否为空
        }
        Long userId = user.getId();
        String key = BLOG_LIKED_KEY + blog.getId();
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
        blog.setIsLike(score != null);
    }
    @Override
    public Result queryHotBlog(Integer current) {
        Page<Blog> page = query().orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        List<Blog> records = page.getRecords();
        records.forEach(blog -> {
            this.queryBlogByUser(blog);
            this.isBlogLiked(blog);
        });
        return Result.ok(records);
    }

    @Override
    public Result queryBlogById(Long id) {
        Blog blog = getById(id);
        if(blog == null){
            return  Result.fail("不存在");
        }
        queryBlogByUser(blog);
        isBlogLiked(blog);
        return Result.ok(blog);
    }

    @Override
    public Result likeBlog(Long id) {
        Long userId = UserHolder.getUser().getId();
        String key = BLOG_LIKED_KEY + id;

        // Boolean isMember = stringRedisTemplate.opsForSet().isMember(key, userId.toString());
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
        if(score == null){
            boolean isSuccess = update().setSql("liked = liked + 1").eq("id", id).update();
            if(isSuccess){
                stringRedisTemplate.opsForZSet().add(key, userId.toString(), System.currentTimeMillis());
            }
        }else{
            boolean isSuccess = update().setSql("liked = liked - 1").eq("id", id).update();
            if(isSuccess){
                stringRedisTemplate.opsForZSet().remove(key, userId.toString());
            }
        }
        return Result.ok();
    }

    @Override
    public Result queryBlogLikes(Long id) {
        String key = BLOG_LIKED_KEY + id;
        Set<String> top5 = stringRedisTemplate.opsForZSet().reverseRange(key, 0, 4);
        if(top5 == null || top5.isEmpty()){
            return Result.ok();
        }
        List<Long> ids = top5.stream().map(Long::valueOf).collect(Collectors.toList());
        String idStr = StrUtil.join(",", ids);
        List<UserDTO> userDTOS = userService.query()
                .in("id", ids)
                .last("ORDER BY FIELD(id" + idStr + ")")
                .list()
                .stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());

        return Result.ok(userDTOS);
    }

    @Override
    public Result saveBlog(Blog blog) {
        Long userId = UserHolder.getUser().getId();
        blog.setUserId(userId);

        boolean isSuccess = save(blog);
        if(!isSuccess){
            return Result.fail("新增笔记失败");
        }
        // 我的所有粉丝
        List<Follow> follows = followService.query().eq("follow_user_id", userId).list();
        for(Follow follow: follows){
            Long followUserId = follow.getUserId();
            String key = FEED + followUserId;
            stringRedisTemplate.opsForZSet().add(key, blog.getId().toString(), System.currentTimeMillis());
        }
        return Result.ok(blog.getId());
    }
}
