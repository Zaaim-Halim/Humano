package com.humano.service.billing;

import com.humano.domain.billing.Coupon;
import com.humano.dto.billing.requests.CreateCouponRequest;
import com.humano.dto.billing.responses.CouponResponse;
import com.humano.repository.billing.CouponRepository;
import com.humano.service.errors.EntityNotFoundException;
import com.humano.web.rest.errors.BadRequestAlertException;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for managing coupons.
 * Handles CRUD operations and coupon validation.
 */
@Service
public class CouponService {

    private static final Logger log = LoggerFactory.getLogger(CouponService.class);
    private static final String ENTITY_NAME = "coupon";

    private final CouponRepository couponRepository;

    public CouponService(CouponRepository couponRepository) {
        this.couponRepository = couponRepository;
    }

    /**
     * Create a new coupon.
     *
     * @param request the coupon creation request
     * @return the created coupon response
     */
    @Transactional
    public CouponResponse createCoupon(CreateCouponRequest request) {
        log.debug("Request to create Coupon: {}", request);

        Coupon coupon = new Coupon();
        coupon.setCode(request.code());
        coupon.setType(request.type());
        coupon.setDiscount(request.discount());
        coupon.setPercentage(request.percentage());
        coupon.setExpiryDate(request.expiryDate());
        coupon.setStartDate(request.startDate() != null ? request.startDate() : Instant.now());
        coupon.setMaxRedemptions(request.maxRedemptions());
        coupon.setTimesRedeemed(0);
        coupon.setActive(true);

        Coupon savedCoupon = couponRepository.save(coupon);
        log.info("Created coupon with ID: {}", savedCoupon.getId());

        return mapToResponse(savedCoupon);
    }

    /**
     * Get a coupon by ID.
     *
     * @param id the ID of the coupon
     * @return the coupon response
     */
    @Transactional(readOnly = true)
    public CouponResponse getCouponById(UUID id) {
        log.debug("Request to get Coupon by ID: {}", id);

        return couponRepository.findById(id).map(this::mapToResponse).orElseThrow(() -> EntityNotFoundException.create("Coupon", id));
    }

    /**
     * Get all coupons with pagination.
     *
     * @param pageable pagination information
     * @return page of coupon responses
     */
    @Transactional(readOnly = true)
    public Page<CouponResponse> getAllCoupons(Pageable pageable) {
        log.debug("Request to get all Coupons");

        return couponRepository.findAll(pageable).map(this::mapToResponse);
    }

    /**
     * Validate and redeem a coupon by code.
     *
     * @param code the coupon code
     * @return the coupon response if valid
     */
    @Transactional
    public CouponResponse redeemCoupon(String code) {
        log.debug("Request to redeem Coupon with code: {}", code);

        return couponRepository
            .findByCodeContainingIgnoreCase(code)
            .stream()
            .filter(c -> c.getCode().equalsIgnoreCase(code))
            .findFirst()
            .map(coupon -> {
                Instant now = Instant.now();

                if (!coupon.isActive()) {
                    throw new BadRequestAlertException("Coupon is not active", ENTITY_NAME, "couponinactive");
                }
                if (coupon.getExpiryDate().isBefore(now)) {
                    throw new BadRequestAlertException("Coupon has expired", ENTITY_NAME, "couponexpired");
                }
                if (coupon.getStartDate() != null && coupon.getStartDate().isAfter(now)) {
                    throw new BadRequestAlertException("Coupon is not yet valid", ENTITY_NAME, "couponnotstarted");
                }
                if (coupon.getMaxRedemptions() != null && coupon.getTimesRedeemed() >= coupon.getMaxRedemptions()) {
                    throw new BadRequestAlertException("Coupon redemption limit reached", ENTITY_NAME, "couponlimitreached");
                }

                coupon.setTimesRedeemed(coupon.getTimesRedeemed() + 1);
                return mapToResponse(couponRepository.save(coupon));
            })
            .orElseThrow(() -> new BadRequestAlertException("Coupon not found", ENTITY_NAME, "couponnotfound"));
    }

    /**
     * Deactivate a coupon.
     *
     * @param id the ID of the coupon to deactivate
     * @return the updated coupon response
     */
    @Transactional
    public CouponResponse deactivateCoupon(UUID id) {
        log.debug("Request to deactivate Coupon: {}", id);

        return couponRepository
            .findById(id)
            .map(coupon -> {
                coupon.setActive(false);
                return mapToResponse(couponRepository.save(coupon));
            })
            .orElseThrow(() -> EntityNotFoundException.create("Coupon", id));
    }

    /**
     * Delete a coupon by ID.
     *
     * @param id the ID of the coupon to delete
     */
    @Transactional
    public void deleteCoupon(UUID id) {
        log.debug("Request to delete Coupon: {}", id);

        if (!couponRepository.existsById(id)) {
            throw EntityNotFoundException.create("Coupon", id);
        }
        couponRepository.deleteById(id);
        log.info("Deleted coupon with ID: {}", id);
    }

    private CouponResponse mapToResponse(Coupon coupon) {
        return new CouponResponse(
            coupon.getId(),
            coupon.getCode(),
            coupon.getType(),
            coupon.getDiscount(),
            coupon.getPercentage(),
            coupon.getStartDate(),
            coupon.getExpiryDate(),
            coupon.isActive(),
            coupon.getMaxRedemptions(),
            coupon.getTimesRedeemed(),
            coupon.getCreatedBy(),
            coupon.getCreatedDate(),
            coupon.getLastModifiedBy(),
            coupon.getLastModifiedDate()
        );
    }
}
