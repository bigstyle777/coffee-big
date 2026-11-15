package com.coffee.config;

import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.BCrypt;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.coffee.entity.SysAdmin;
import com.coffee.mapper.SysAdminMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

/**
 * 初始店长后台账号创建
 * 仅在 sys_admin 为空时执行一次，已有账号时不会覆盖密码
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminBootstrapRunner implements ApplicationRunner {

    private static final int ADMIN_ENABLED = 1;

    private final SysAdminMapper adminMapper;
    private final AdminProperties adminProperties;

    @Override
    public void run(ApplicationArguments args) {
        long adminCount = adminMapper.selectCount(Wrappers.<SysAdmin>lambdaQuery());
        if (adminCount > 0) {
            return;
        }
        String username = adminProperties.defaultUsername();
        String password = adminProperties.defaultPassword();
        if (StrUtil.isBlank(username) || StrUtil.isBlank(password)) {
            log.warn("后台账号表为空，且未配置 ADMIN_USERNAME / ADMIN_PASSWORD，跳过初始化");
            return;
        }

        SysAdmin admin = new SysAdmin();
        admin.setUsername(username.trim());
        admin.setPasswordHash(BCrypt.hashpw(password));
        admin.setNickname("店长");
        admin.setStatus(ADMIN_ENABLED);
        try {
            adminMapper.insert(admin);
            log.warn("已创建初始店长后台账号 username={}，请登录后尽快修改默认密码", username);
        } catch (DuplicateKeyException exception) {
            log.info("店长后台账号已存在，跳过初始化 username={}", username);
        }
    }
}
