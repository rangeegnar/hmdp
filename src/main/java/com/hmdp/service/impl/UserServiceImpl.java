package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.constant.ErrorConstants;
import com.hmdp.constant.RedisConstants;
import com.hmdp.constant.SystemConstants;
import static com.hmdp.constant.RedisConstants.USER_SIGN_KEY;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;


@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    //Lua 脚本：查询在线设备并原子清理僵尸 token（getOnlineDevices.lua）
    private static final DefaultRedisScript<String> ONLINE_DEVICES_SCRIPT = new DefaultRedisScript<>();
    // Lua 脚本：登录超量踢出 + 登记设备（loginTokenKick.lua）
    private static final DefaultRedisScript<String> LOGIN_TOKEN_KICK_SCRIPT = new DefaultRedisScript<>();

    static {
        ONLINE_DEVICES_SCRIPT.setLocation(new ClassPathResource("getOnlineDevices.lua"));
        ONLINE_DEVICES_SCRIPT.setResultType(String.class);

        LOGIN_TOKEN_KICK_SCRIPT.setLocation(new ClassPathResource("loginTokenKick.lua"));
        LOGIN_TOKEN_KICK_SCRIPT.setResultType(String.class);
    }
    /**
     * 发送验证码
     * @param phone
     * @param session
     * @return
     */
    @Override
    public Result sendCode(String phone, HttpSession session) {
        //1.校验手机号
        if(RegexUtils.isPhoneInvalid(phone)) {
            //2.不符合，返回错误
            return Result.fail(ErrorConstants.PHONE_INVALID);
        }

        //3.符合，生成验证码
        String code = RandomUtil.randomNumbers(6);
        //4.保存验证码到redis
       stringRedisTemplate.opsForValue().set(RedisConstants.LOGIN_CODE_KEY + phone,code,
               RedisConstants.LOGIN_CODE_TTL, TimeUnit.MINUTES);
        //5.发送验证码
        log.info("短信验证码发送成功：{}",code);

        return Result.ok();

    }


    /**
     * 登录
     * @param loginForm
     * @param session
     * @return
     */
    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        String code = loginForm.getCode();
        String phone = loginForm.getPhone();
        //1.校验手机号
        if(RegexUtils.isPhoneInvalid(phone)) {
            //2.不符合，返回错误
            return Result.fail(ErrorConstants.PHONE_INVALID);
        }
        //3.校验验证码
        String cacheCode = stringRedisTemplate.opsForValue().get(RedisConstants.LOGIN_CODE_KEY+phone);
        if(cacheCode==null||!cacheCode.equals(code)){
           return Result.fail(ErrorConstants.CODE_INVALID);
       }

        //4.一致，根据手机号查询用户
        User user = query().eq("phone",phone).one();

        //5.判断用户是否存在
        //6.不存在，创建新用户，保存到数据库
        if(user==null){
           user=createUserWithPhone(phone);
        }

        //7. 保存用户信息到 Redis 并返回 Token
        return createTokenAndSaveUser(user);
    }

    /**
     * 使用密码登录
     * @param loginFormDTO
     * @param session
     * @return
     */
    @Override
    public Result loginWithPassword(LoginFormDTO loginFormDTO, HttpSession session) {
        String phone = loginFormDTO.getPhone();
        String password = loginFormDTO.getPassword();

        // 1. 校验手机号格式
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail(ErrorConstants.PHONE_INVALID);
        }

        // 2. 校验密码非空
        if (StrUtil.isBlank(password)) {
            return Result.fail(ErrorConstants.PASSWORD_BLANK);
        }

        // 3. 根据手机号查库
        User user = query().eq("phone", phone).one();
        if (user == null) {
            return Result.fail(ErrorConstants.USER_OR_PASSWORD_ERROR);
        }

        // 4. 校验密码是否匹配
        boolean matches = PasswordEncoder.matches(user.getPassword(), password);
        if (!matches) {
            return Result.fail(ErrorConstants.USER_OR_PASSWORD_ERROR);
        }

        // 5. 保存用户信息到 Redis 并返回 Token
        return createTokenAndSaveUser(user);
    }

    /**
     * 统一生成 Token 并保存 UserDTO 到 Redis（支持多设备管控与超量踢出）
     */
    private Result createTokenAndSaveUser(User user) {
        Long userId = user.getId();
        String userTokensKey = RedisConstants.LOGIN_USER_TOKENS_KEY + userId;

        // 1. 生成随机 Token 作为当前设备的登录令牌
        String token = UUID.randomUUID().toString(true);

        // 2. 将 User 转为 UserDTO
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);

        // 3. 将 UserDTO 转为 Map，确保 value 为 String 类型以适配 StringRedisTemplate
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(),
                CopyOptions.create()
                        .setIgnoreNullValue(true)
                        .setFieldValueEditor((fieldName, fieldValue) -> fieldValue != null ? fieldValue.toString() : null));

        // 4. 保存 Token 详情到 Redis (Hash 结构)，并设置 30 分钟有效期
        String tokenKey = RedisConstants.LOGIN_USER_KEY + token;
        stringRedisTemplate.opsForHash().putAll(tokenKey, userMap);
        stringRedisTemplate.expire(tokenKey, RedisConstants.LOGIN_USER_TTL, TimeUnit.MINUTES);

        // 5. 原子执行 Lua：超量自动踢出最早设备 + 登记新 Token + 设备集合兜底过期 zCard 检查 / range 取旧 / delete 三步
        long now = System.currentTimeMillis();
        String kicked = stringRedisTemplate.execute(
                LOGIN_TOKEN_KICK_SCRIPT,
                Collections.singletonList(userTokensKey),
                String.valueOf(RedisConstants.MAX_ONLINE_DEVICES),
                token,
                RedisConstants.LOGIN_USER_KEY,
                String.valueOf(now),
                String.valueOf(TimeUnit.DAYS.toSeconds(RedisConstants.LOGIN_USER_TOKENS_TTL)));
        if (StrUtil.isNotBlank(kicked)) {
            log.info("用户 [{}] 在线设备数达到上限 {}，已自动将最早登录的设备 [{}] 踢下线",
                    userId, RedisConstants.MAX_ONLINE_DEVICES, kicked);
        }

        // 6. 返回 Token
        return Result.ok(token);
    }

    @Override
    public Result logout(String token) {
        if (StrUtil.isNotBlank(token)) {
            if (token.startsWith("Bearer ")) {
                token = token.substring(7);
            }
            // 1. 删除当前设备的 Token
            stringRedisTemplate.delete(RedisConstants.LOGIN_USER_KEY + token);
            // 2. 从用户的设备集合中移除当前 Token
            UserDTO user = UserHolder.getUser();
            if (user != null && user.getId() != null) {
                stringRedisTemplate.opsForZSet().remove(RedisConstants.LOGIN_USER_TOKENS_KEY + user.getId(), token);
            }
        }
        UserHolder.removeUser();
        return Result.ok();
    }

    @Override
    public Result kickAll(Long userId) {
        if (userId == null) {
            return Result.fail("用户ID不能为空");
        }
        String userTokensKey = RedisConstants.LOGIN_USER_TOKENS_KEY + userId;
        // 1. 获取该用户所有在线设备的 Token
        Set<String> tokens = stringRedisTemplate.opsForZSet().range(userTokensKey, 0, -1);
        if (tokens != null && !tokens.isEmpty()) {
            List<String> keys = tokens.stream()
                    .map(t -> RedisConstants.LOGIN_USER_KEY + t)
                    .collect(Collectors.toList());
            // 2. 批量删除所有设备的 Token 详情
            stringRedisTemplate.delete(keys);
        }
        // 3. 删除用户设备集合
        stringRedisTemplate.delete(userTokensKey);
        UserHolder.removeUser();
        return Result.ok("已成功将该账号的所有设备强制下线");
    }

    @Override
    public Result kickDevice(String token) {
        if (StrUtil.isBlank(token)) {
            return Result.fail("Token不能为空");
        }
        if (token.startsWith("Bearer ")) {
            token = token.substring(7);
        }
        // 1. 删除该设备的 Token
        stringRedisTemplate.delete(RedisConstants.LOGIN_USER_KEY + token);
        // 2. 从该用户的设备集合中移除
        UserDTO user = UserHolder.getUser();
        if (user != null && user.getId() != null) {
            stringRedisTemplate.opsForZSet().remove(RedisConstants.LOGIN_USER_TOKENS_KEY + user.getId(), token);
        }
        return Result.ok("指定设备已成功下线");
    }

    @Override
    public Result getOnlineDevices() {
        UserDTO user = UserHolder.getUser();
        if (user == null || user.getId() == null) {
            return Result.fail("用户未登录");
        }
        String userTokensKey = RedisConstants.LOGIN_USER_TOKENS_KEY + user.getId();

        // 原子执行 Lua：一次往返完成「取全部设备 + 批量判断存活 + 惰性清理僵尸 token」
        String json = stringRedisTemplate.execute(
                ONLINE_DEVICES_SCRIPT,
                Collections.singletonList(userTokensKey),
                RedisConstants.LOGIN_USER_KEY);
        if (StrUtil.isBlank(json)) {
            return Result.ok(Collections.emptyList());
        }

        // 解析 Lua 返回的 JSON：{"deviceList":[["token",loginTime],...],"expiredTokens":["token",...]}
        JSONObject jsonObject = JSONUtil.parseObj(json);
        JSONArray deviceArray = jsonObject.getJSONArray("deviceList");
        if (deviceArray == null || deviceArray.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }

        List<Map<String, Object>> deviceList = new ArrayList<>(deviceArray.size());
        for (Object item : deviceArray) {
            JSONArray device = (JSONArray) item;
            Map<String, Object> map = new HashMap<>();
            map.put("token", device.getStr(0));
            map.put("loginTime", device.getLong(1));
            deviceList.add(map);
        }

        return Result.ok(deviceList);
    }

    @Override
    public Result sign() {
        //1.获取当前登录用户
        Long userId = UserHolder.getUser().getId();
        //2.获取日期
        LocalDateTime now = LocalDateTime.now();
        //3.拼接key
        String keySuffix = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        String key = USER_SIGN_KEY + userId + keySuffix;
        //4.获取今天是这个月的第几天
        int dayOfMonth = now.getDayOfMonth();
        //5.写入redis setbit key offset 1
        stringRedisTemplate.opsForValue().setBit(key,dayOfMonth-1,true);
        return Result.ok();
    }

    @Override
    public Result signCount() {
        //1.获取当前登录用户
        Long userId = UserHolder.getUser().getId();
        //2.获取日期
        LocalDateTime now = LocalDateTime.now();
        //3.拼接key
        String keySuffix = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        String key = USER_SIGN_KEY + userId + keySuffix;
        //4.获取今天是这个月的第几天
        int dayOfMonth = now.getDayOfMonth();
        //5.获取本月截止今天为止所有的签到记录，返回的是一个十进制的数字
        List<Long> result = stringRedisTemplate.opsForValue()
                .bitField(key, BitFieldSubCommands.create()
                        .get(BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth))
                        .valueAt(0));
        if(result==null||result.isEmpty()){
            //没有任何签到结果
            return Result.ok(0);
        }
        Long num = result.get(0);
        if(num==0||num==null){
            return Result.ok(0);
        }
        //6.循坏遍历
        int count=0;
        while (true) {
            //让这个数字与1做与运算，得到数字的最后一个bit位，判断这个bit是否为0
            if((num&1)==0) {
                //如果为0，未签到
                break;
            }else {
                //如果不为0，已签到，计算器+1
                count++;
                //把数字右移一位，抛弃最后一个bit位，继续下一个bit位
                num>>>=1;
            }
        }
        return Result.ok(count);

    }

    private User createUserWithPhone(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName(SystemConstants.USER_NICK_NAME_PREFIX +RandomUtil.randomString(10));
        //保存
        save(user);
        return user;

    }
}
