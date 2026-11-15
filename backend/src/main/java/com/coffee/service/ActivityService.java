package com.coffee.service;

import com.coffee.dto.admin.BindActivityProductRequest;
import com.coffee.dto.admin.SaveActivityRequest;
import com.coffee.dto.admin.UpdateActivityProductRequest;
import com.coffee.vo.activity.SeckillActivityVO;
import com.coffee.vo.admin.AdminActivityDetailVO;
import com.coffee.vo.admin.AdminActivityVO;
import com.coffee.vo.admin.PageVO;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 营销活动后台管理
 */
public interface ActivityService {

    /**
     * 活动分页查询
     *
     * @param type   活动类型，可为空
     * @param status 实时状态：NOT_STARTED / RUNNING / ENDED / CLOSED，可为空
     * @param isOpen 开关筛选，可为空
     */
    PageVO<AdminActivityVO> pageActivities(long pageNum, long pageSize, String keyword,
                                           String type, String status, Integer isOpen);

    /**
     * 活动详情，包含绑定商品与预热状态
     */
    AdminActivityDetailVO getActivityDetail(Long activityId);

    /**
     * 新建活动，默认关闭
     *
     * @return 活动 ID
     */
    Long createActivity(SaveActivityRequest request);

    /**
     * 编辑活动；活动结束只关闭，不删除
     */
    void updateActivity(Long activityId, SaveActivityRequest request);

    /**
     * 开启 / 关闭活动；开启秒杀前会校验商品与预热状态
     */
    void updateOpen(Long activityId, Boolean isOpen);

    /**
     * 绑定活动商品；活动开启中不允许修改
     */
    void bindProduct(Long activityId, BindActivityProductRequest request);

    /**
     * 修改秒杀商品的活动价、限量与限购；修改后需要重新预热
     */
    void updateActivityProduct(Long activityId, Long bindingId,
                               UpdateActivityProductRequest request);

    /**
     * 停用活动商品绑定，不物理删除
     */
    void disableActivityProduct(Long activityId, Long bindingId);

    /**
     * 一键预热秒杀库存，返回预热后的活动详情
     */
    AdminActivityDetailVO preheat(Long activityId);

    /**
     * 查询预热状态，返回活动详情
     */
    AdminActivityDetailVO getPreheatStatus(Long activityId);

    /**
     * 当前正在进行的秒杀商品 ID，普通下单需要拦截这些商品
     */
    Set<Long> listActiveSeckillProductIds();

    /**
     * 计算满减优惠：返回优惠最大的活动
     *
     * @param productSubtotals 商品 ID -> 小计金额（分）
     */
    FullReductionResult calculateFullReduction(Map<Long, Long> productSubtotals);

    /**
     * 顾客端秒杀活动列表：只返回进行中的秒杀活动与上架商品
     */
    List<SeckillActivityVO> listRunningSeckillActivities();

    /**
     * 满减计算结果
     *
     * @param activityId     命中的活动 ID，未命中为 null
     * @param discountAmount 优惠金额（分）
     */
    record FullReductionResult(Long activityId, long discountAmount) {

        public static FullReductionResult none() {
            return new FullReductionResult(null, 0L);
        }
    }
}
