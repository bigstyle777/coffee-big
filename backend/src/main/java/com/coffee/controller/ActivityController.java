package com.coffee.controller;

import com.coffee.common.Result;
import com.coffee.service.ActivityService;
import com.coffee.vo.activity.SeckillActivityVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 顾客端活动接口
 */
@RestController
@RequestMapping("/api/activity")
@RequiredArgsConstructor
public class ActivityController {

    private final ActivityService activityService;

    /**
     * 进行中的秒杀活动与商品，公开接口，无需登录
     */
    @GetMapping("/seckill/list")
    public Result<List<SeckillActivityVO>> listSeckillActivities() {
        return Result.ok(activityService.listRunningSeckillActivities());
    }
}
