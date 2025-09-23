package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.properties.JwtProperties;
import com.hmdp.service.IUserService;
import com.hmdp.utils.JwtClaimsConstant;
import com.hmdp.utils.JwtUtil;
import com.hmdp.utils.RegexUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;
import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User>
        implements IUserService {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private RedisTemplate<String,String> redisTemplate;
    @Autowired
    private JwtProperties jwtProperties;

    /**
     * 发送手机验证码
     * @param phone 手机号
     * @param session 会话
     * @return 结果
     */
    @Override
    public Result sendCode(String phone, HttpSession session) {
        // 1. 校验手机号格式
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式错误");
        }

        // 2. 检查发送频率 - 防止短信轰炸
        String rateLimitKey = LOGIN_CODE_RATE_LIMIT_KEY + phone;
        String rateLimitValue = redisTemplate.opsForValue().get(rateLimitKey);
        if (rateLimitValue != null) {
            // 如果存在key，说明1分钟内已经发过，拒绝请求
            return Result.fail("发送过于频繁，请稍后再试");
        }

        // 3. 生成验证码
        String code = RandomUtil.randomNumbers(6);

        // 4. 【关键】保存验证码到Redis，以手机号为Key，并设置有效期
        String redisKey = LOGIN_CODE_KEY + phone;
        redisTemplate.opsForValue().set(redisKey, code, LOGIN_CODE_TTL, TimeUnit.MINUTES);

        // 5. 【关键】设置频率限制的Key，有效期1分钟
        redisTemplate.opsForValue().set(rateLimitKey, "1", RATE_LIMIT_TTL, TimeUnit.MINUTES);

        // 6. 发送验证码 (TODO 接入阿里云短信服务)
        log.debug("发送短信验证码成功，手机号：{}， 验证码：{}", phone, code);
        // 实际生产中，这里应该调用短信服务API，并做好异常处理

        return Result.ok();
    }

    /**
     * 登录功能
     * @param loginForm 登录参数，包含手机号、验证码；或者手机号、密码
     *
     * @return 结果
     */
    @Override
    public Result login(LoginFormDTO loginForm) {
        String phone = loginForm.getPhone();
        String inputCode = loginForm.getCode();

        // 1. 校验手机号格式
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式错误");
        }

        // 2. 从Redis中获取验证码
        String redisKey = LOGIN_CODE_KEY + phone;
        String correctCode = redisTemplate.opsForValue().get(redisKey);

        // 3. 验证码校验（统一提示，防止探测）
        if (correctCode == null || !correctCode.equals(inputCode)) {
            // 验证码为空（已过期）或不匹配
            return Result.fail("验证码错误或已过期");
        }

        // 4. 【关键】验证通过，立即删除Redis中的验证码，确保一次性使用
        redisTemplate.delete(redisKey);

        // 5. 查询用户是否存在
        User user = query().eq("phone", phone).one();

        if (user == null) {
            // 5.1 【自动注册逻辑】用户不存在，创建新用户
            user = createUserWithPhone(phone);
        }

        //生成jwt
        Map<String, Object> claims = new HashMap<>();
        claims.put(JwtClaimsConstant.USER_ID, user.getId());
        String jwttoken = JwtUtil.createJWT(jwtProperties.getUserSecretKey(),jwtProperties.getUserTtl(),claims);

        UserDTO userDTO = new UserDTO();
        BeanUtil.copyProperties(user, userDTO);

        //将user对象转为HashMap
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(),
                CopyOptions.create()
                        .setIgnoreNullValue(true)
                        .setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString()));

       //存储
        String tokenKey = LOGIN_USER_KEY + userDTO.getId();
        // 7.4.将jwttoken存入userMap中
        userMap.put("jwttoken",jwttoken);
        redisTemplate.opsForHash().putAll(tokenKey, userMap);
        // 7.5.设置redis中 userId的有效期
        redisTemplate.expire(tokenKey, LOGIN_USER_TTL, TimeUnit.SECONDS);

        return Result.ok(jwttoken);
    }

    /**
     * 根据手机号创建新用户
     * @param phone 手机号
     * @return 新用户对象
     */
    private User createUserWithPhone(String phone) {
        User user = new User();
        user.setPhone(phone);
        // 随机生成昵称
        user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
        // 保存用户到数据库
        userMapper.insert(user);
        return user;
    }
}