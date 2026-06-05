package com.humano.web.rest.billing;

import com.humano.dto.billing.requests.CreateCouponRequest;
import com.humano.dto.billing.responses.CouponResponse;
import com.humano.security.AuthoritiesConstants;
import com.humano.service.billing.CouponService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Coupon management. Coupons are platform-level constructs (tenants don't issue
 * their own); all endpoints require {@code ROLE_ADMIN}. The {@code POST /redeem/{code}}
 * endpoint is the only one any authenticated user can hit — it consumes a coupon and
 * returns the redemption state, intended to be wired into a subscription or invoice flow
 * by the caller.
 */
@RestController
@RequestMapping("/api/billing/coupons")
@PreAuthorize("hasAuthority(\"" + AuthoritiesConstants.ADMIN + "\")")
public class CouponResource {

    private static final Logger LOG = LoggerFactory.getLogger(CouponResource.class);

    private final CouponService couponService;

    public CouponResource(CouponService couponService) {
        this.couponService = couponService;
    }

    @GetMapping
    public ResponseEntity<Page<CouponResponse>> list(Pageable pageable) {
        return ResponseEntity.ok(couponService.getAllCoupons(pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<CouponResponse> get(@PathVariable UUID id) {
        return ResponseEntity.ok(couponService.getCouponById(id));
    }

    @PostMapping
    public ResponseEntity<CouponResponse> create(@Valid @RequestBody CreateCouponRequest request) {
        LOG.debug("REST request to create Coupon: {}", request);
        CouponResponse created = couponService.createCoupon(request);
        return ResponseEntity.created(URI.create("/api/billing/coupons/" + created.id())).body(created);
    }

    @PostMapping("/redeem/{code}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<CouponResponse> redeem(@PathVariable String code) {
        return ResponseEntity.ok(couponService.redeemCoupon(code));
    }

    @PostMapping("/{id}/deactivate")
    public ResponseEntity<CouponResponse> deactivate(@PathVariable UUID id) {
        return ResponseEntity.ok(couponService.deactivateCoupon(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        couponService.deleteCoupon(id);
        return ResponseEntity.noContent().build();
    }
}
