package com.coffee;

import com.coffee.config.AdminProperties;
import com.coffee.config.AuthProperties;
import com.coffee.config.OrderProperties;
import com.coffee.config.PaymentProperties;
import com.coffee.config.UploadProperties;
import com.coffee.config.WechatProperties;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Coffee 咖啡点单小程序后端启动类
 */
@EnableAsync
@EnableScheduling
@MapperScan("com.coffee.mapper")
@EnableConfigurationProperties({AuthProperties.class, AdminProperties.class,
        WechatProperties.class, OrderProperties.class, UploadProperties.class,
        PaymentProperties.class})
@SpringBootApplication
public class CoffeeBigApplication {

    public static void main(String[] args) {
        SpringApplication.run(CoffeeBigApplication.class, args);
    }
}
