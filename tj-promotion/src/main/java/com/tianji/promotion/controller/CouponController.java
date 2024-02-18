package com.tianji.promotion.controller;


import com.tianji.common.domain.dto.PageDTO;
import com.tianji.promotion.domain.dto.CouponFormDTO;
import com.tianji.promotion.domain.dto.CouponIssueFormDTO;
import com.tianji.promotion.domain.query.CouponQuery;
import com.tianji.promotion.domain.vo.CouponDetailVO;
import com.tianji.promotion.domain.vo.CouponPageVO;
import com.tianji.promotion.domain.vo.CouponVO;
import com.tianji.promotion.service.ICouponService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.apache.ibatis.annotations.Param;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

/**
 * <p>
 * 优惠券的规则信息 前端控制器
 * </p>
 *
 * @author QianCCC
 * @since 2024-02-07
 */
@RestController
@RequestMapping("/coupons")
@Api(tags = "优惠券相关接口")
@RequiredArgsConstructor
public class CouponController {
    private final ICouponService couponService;

    @ApiOperation("新增优惠券")
    @PostMapping
    public void saveCoupon(@RequestBody @Valid CouponFormDTO couponFormDTO) {
        couponService.saveCoupon(couponFormDTO);
    }

    @ApiOperation("分页查询优惠券")
    @GetMapping("/page")
    public PageDTO<CouponPageVO> queryCouponByPage(CouponQuery query) {
        return couponService.queryCouponByPage(query);
    }

    @ApiOperation("发放优惠券")
    @PutMapping("/{id}/issue")
    public void grantCoupon(@RequestBody @Valid CouponIssueFormDTO couponIssueFormDTO) {
        couponService.grantCoupon(couponIssueFormDTO);
    }

    @ApiOperation("修改优惠券")
    @PutMapping("/{id}")
    public void updateCoupon(@RequestBody @Valid CouponFormDTO couponFormDTO) {
        couponService.updateCoupon(couponFormDTO);
    }

    @ApiOperation("删除优惠券")
    @DeleteMapping("/{id}")
    public void deleteCouponById(@PathVariable("id") Long id) {
        couponService.deleteCouponById(id);
    }

    @ApiOperation("根据id查询指定优惠券信息")
    @GetMapping("/{id}")
    public CouponDetailVO queryCouponDetailById(@PathVariable("id") Long id) {
        return couponService.queryCouponDetailById(id);
    }

    @ApiOperation("暂停发放优惠券")
    @PutMapping("/{id}/pause")
    public void pauseIssueCoupon(@PathVariable("id") Long id) {
        couponService.pauseIssueCoupon(id);
    }

    @ApiOperation("用户端查看发放中且手动领取的优惠券")
    @GetMapping("/list")
    public List<CouponVO> queryIssuingCoupon() {
        return couponService.queryIssuingCoupon();
    }
}
