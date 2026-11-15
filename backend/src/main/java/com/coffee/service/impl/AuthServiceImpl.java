package com.coffee.service.impl;

import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.coffee.client.WechatAuthClient;
import com.coffee.common.BusinessException;
import com.coffee.config.AuthProperties;
import com.coffee.dto.auth.WxLoginRequest;
import com.coffee.entity.SysUser;
import com.coffee.enums.UserRole;
import com.coffee.mapper.SysUserMapper;
import com.coffee.security.JwtUtils;
import com.coffee.service.AuthService;
import com.coffee.service.CouponService;
import com.coffee.vo.auth.LoginVO;
import com.coffee.vo.auth.UserInfoVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

/**
 * 认证业务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    /** 用户正常状态 */
    private static final int USER_ENABLED = 1;
    /** 店长角色 */
    private static final String ADMIN_ROLE = UserRole.ADMIN.name();
    /** 体验登录固定 openid */
    private static final String DEV_LOGIN_OPENID = "dev-login-openid";

    private final SysUserMapper userMapper;
    private final WechatAuthClient wechatAuthClient;
    private final JwtUtils jwtUtils;
    private final AuthProperties authProperties;
    private final CouponService couponService;

    @Override
    public LoginVO wxLogin(WxLoginRequest request) {
        if (request == null || StrUtil.isBlank(request.code())) {
            throw new BusinessException("微信登录 code 不能为空");
        }
        // 用 code 换取 openid，服务端不保存 session_key
        String openid = wechatAuthClient.getOpenidByCode(request.code().trim());
        SysUser user = findByOpenid(openid);
        if (user == null) {
            user = registerCustomer(openid);
            log.info("微信用户自动注册成功 userId={}", user.getId());
        }
        if (user.getStatus() == null || user.getStatus() != USER_ENABLED) {
            throw new BusinessException("账号已被禁用，请联系店长");
        }
        applyBootstrapAdmin(user);
        couponService.grantWelcomeCouponIfNeeded(user.getId());

        String token = jwtUtils.generateToken(user.getId(), user.getRole());
        return new LoginVO(token, user.getRole(),
                new UserInfoVO(user.getId(), user.getNickname(), user.getAvatar(),
                        user.getPhone(), user.getRole()));
    }

    @Override
    public LoginVO devLogin() {
        if (!authProperties.devLoginEnabled()) {
            throw new BusinessException("体验登录未开启");
        }
        SysUser user = findByOpenid(DEV_LOGIN_OPENID);
        if (user == null) {
            user = createDevUser();
        }
        if (!ADMIN_ROLE.equals(user.getRole())) {
            SysUser updateUser = new SysUser();
            updateUser.setId(user.getId());
            updateUser.setRole(ADMIN_ROLE);
            updateUser.setStatus(USER_ENABLED);
            userMapper.updateById(updateUser);
            user.setRole(ADMIN_ROLE);
            user.setStatus(USER_ENABLED);
        }
        couponService.grantWelcomeCouponIfNeeded(user.getId());
        String token = jwtUtils.generateToken(user.getId(), user.getRole());
        log.info("体验登录成功 userId={}", user.getId());
        return new LoginVO(token, user.getRole(),
                new UserInfoVO(user.getId(), user.getNickname(), user.getAvatar(),
                        user.getPhone(), user.getRole()));
    }

    /**
     * 创建体验店长账号，唯一索引兜底并发首次登录
     */
    private SysUser createDevUser() {
        SysUser newUser = new SysUser();
        newUser.setOpenid(DEV_LOGIN_OPENID);
        newUser.setNickname("体验店长");
        newUser.setAvatar("");
        newUser.setRole(ADMIN_ROLE);
        newUser.setStatus(USER_ENABLED);
        try {
            userMapper.insert(newUser);
            return newUser;
        } catch (DuplicateKeyException exception) {
            SysUser existingUser = findByOpenid(DEV_LOGIN_OPENID);
            if (existingUser == null) {
                throw new BusinessException("体验登录失败，请稍后重试");
            }
            return existingUser;
        }
    }

    /**
     * 初始店长激活：配置 ADMIN_OPENID 的微信用户首次登录时自动提升为店长
     * 仅用于新环境初始化，激活后可从环境变量中移除该配置
     */
    private void applyBootstrapAdmin(SysUser user) {        String adminOpenid = authProperties.adminOpenid();
        if (StrUtil.isBlank(adminOpenid) || !adminOpenid.equals(user.getOpenid())) {
            return;
        }
        if (ADMIN_ROLE.equals(user.getRole())) {
            return;
        }
        SysUser updateUser = new SysUser();
        updateUser.setId(user.getId());
        updateUser.setRole(ADMIN_ROLE);
        userMapper.updateById(updateUser);
        user.setRole(ADMIN_ROLE);
        log.info("初始店长账号已激活 userId={}", user.getId());
    }

    private SysUser findByOpenid(String openid) {
        return userMapper.selectOne(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getOpenid, openid));
    }

    /**
     * 自动注册顾客账号：唯一索引兜底并发首次登录
     */
    private SysUser registerCustomer(String openid) {
        SysUser newUser = new SysUser();
        newUser.setOpenid(openid);
        newUser.setNickname("咖啡新客" + RandomUtil.randomNumbers(4));
        newUser.setAvatar("");
        newUser.setRole(UserRole.CUSTOMER.name());
        newUser.setStatus(USER_ENABLED);
        try {
            userMapper.insert(newUser);
            return newUser;
        } catch (DuplicateKeyException exception) {
            SysUser existingUser = findByOpenid(openid);
            if (existingUser == null) {
                throw new BusinessException("用户注册失败，请稍后重试");
            }
            return existingUser;
        }
    }
}
