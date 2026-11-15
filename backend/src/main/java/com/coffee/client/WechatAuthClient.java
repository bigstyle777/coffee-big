package com.coffee.client;

import cn.hutool.core.util.StrUtil;
import com.coffee.common.BusinessException;
import com.coffee.config.WechatProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Map;

/**
 * 微信开放接口客户端
 */
@Slf4j
@Component
public class WechatAuthClient {

    private static final String GRANT_TYPE = "authorization_code";

    private final WechatProperties wechatProperties;
    private final RestClient restClient;

    public WechatAuthClient(WechatProperties wechatProperties) {
        this.wechatProperties = wechatProperties;
        this.restClient = RestClient.create();
    }

    /**
     * 使用小程序登录 code 换取 openid
     */
    public String getOpenidByCode(String code) {
        if (StrUtil.isBlank(wechatProperties.appId()) || StrUtil.isBlank(wechatProperties.appSecret())) {
            throw new BusinessException("微信登录未配置，请先设置 WX_APP_ID 与 WX_APP_SECRET");
        }

        Map<String, Object> response;
        try {
            response = restClient.get()
                    .uri(wechatProperties.jscode2sessionUrl()
                                    + "?appid={appid}&secret={secret}&js_code={code}&grant_type={grantType}",
                            wechatProperties.appId(), wechatProperties.appSecret(), code, GRANT_TYPE)
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {
                    });
        } catch (RestClientException exception) {
            log.error("调用微信 jscode2session 失败", exception);
            throw new BusinessException("微信登录服务暂时不可用，请稍后重试");
        }

        if (response == null) {
            throw new BusinessException("微信登录失败：未收到响应");
        }
        Object errcode = response.get("errcode");
        if (errcode != null && !"0".equals(String.valueOf(errcode))) {
            log.warn("微信 jscode2session 返回错误：errcode={}, errmsg={}", errcode, response.get("errmsg"));
            throw new BusinessException("微信登录失败，请重新进入小程序");
        }
        Object openid = response.get("openid");
        if (openid == null || StrUtil.isBlank(String.valueOf(openid))) {
            throw new BusinessException("微信登录失败：未获取到 openid");
        }
        return String.valueOf(openid);
    }
}
