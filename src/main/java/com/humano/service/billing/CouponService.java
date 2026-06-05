package com.humano.service.billing;

import com.humano.domain.billing.Coupon;
import com.humano.domain.enumeration.billing.DiscountType;
import com.humano.dto.billing.requests.CreateCouponRequest;
import com.humano.dto.billing.responses.CouponResponse;
import com.humano.repository.billing.CouponRepository;
import com.humano.service.errors.EntityNotFoundException;
import com.humano.web.rest.errors.BadRequestAlertException;
import java.math.BigDecimal;
import java.math.RoundingMode;
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
     * Validate and redeem a coupon by code. Increments {@code timesRedeemed} on success.
     * Returns the coupon response if valid; throws {@link BadRequestAlertException}
     * (HTTP 400 ProblemDetail in the response) for any of: unknown code, inactive,
     * expired, not-yet-started, or redemption-limit-reached.
     */
    @Transactional
    public CouponResponse redeemCoupon(String code) {
        log.debug("Request to redeem Coupon with code: {}", code);
        Coupon coupon = findAndValidate(code);
        coupon.setTimesRedeemed(coupon.getTimesRedeemed() + 1);
        return mapToResponse(couponRepository.save(coupon));
    }

    /**
     * Result of a successful coupon application. The {@code coupon} reference lets the
     * caller persist the snapshot code on the consuming entity; {@code discountAmount}
     * is what to subtract from the subtotal.
     */
    public record CouponApplication(Coupon coupon, BigDecimal discountAmount) {}

    /**
     * Validates + redeems {@code code}, then computes the discount for {@code subtotal}.
     * Used at invoice issuance to apply a coupon (P4.5). Atomic: the redemption count
     * is incremented in the same transaction that computes the discount, so a
     * concurrent over-redemption is bounded by the optimistic-lock {@code @Version}.
     *
     * <p>Calculation:
     * <ul>
     *   <li>{@link DiscountType#FIXED} — discount = {@code coupon.discount}, clamped to
     *       not exceed {@code subtotal} (we never produce a negative invoice).</li>
     *   <li>{@link DiscountType#PERCENT} — discount =
     *       {@code subtotal × (percentage/100)} at scale 4 HALF_UP.</li>
     * </ul>
     *
     * @throws BadRequestAlertException (mapped to HTTP 400) for any validation failure
     *         or {@code subtotal <= 0}.
     */
    @Transactional
    public CouponApplication applyToAmount(String code, BigDecimal subtotal) {
        log.debug("Applying coupon {} to subtotal {}", code, subtotal);
        if (subtotal == null || subtotal.signum() <= 0) {
            throw new BadRequestAlertException("Cannot apply coupon to non-positive subtotal", ENTITY_NAME, "couponbadsubtotal");
        }
        Coupon coupon = findAndValidate(code);
        BigDecimal discount = computeDiscount(coupon, subtotal);
        coupon.setTimesRedeemed(coupon.getTimesRedeemed() + 1);
        couponRepository.save(coupon);
        return new CouponApplication(coupon, discount);
    }

    /**
     * Same validation as {@link #applyToAmount} but without redemption or computation.
     * Use to pre-validate a code (e.g. on a checkout form's "apply coupon" preview
     * before the user commits to the purchase).
     */
    @Transactional(readOnly = true)
    public CouponResponse validateOnly(String code) {
        return mapToResponse(findAndValidate(code));
    }

    /**
     * Strict lookup + validation. Single source of truth for the rules; called by
     * {@link #redeemCoupon}, {@link #applyToAmount}, and {@link #validateOnly}.
     */
    private Coupon findAndValidate(String code) {
        if (code == null || code.isBlank()) {
            throw new BadRequestAlertException("Coupon code is required", ENTITY_NAME, "couponcodemissing");
        }
        Coupon coupon = couponRepository
            .findByCodeContainingIgnoreCase(code)
            .stream()
            .filter(c -> c.getCode().equalsIgnoreCase(code))
            .findFirst()
            .orElseThrow(() -> new BadRequestAlertException("Coupon not found", ENTITY_NAME, "couponnotfound"));
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
        return coupon;
    }

    /**
     * Computes the discount amount for a coupon against a subtotal. FIXED discounts are
     * clamped to never exceed the subtotal (no negative invoices). PERCENT discounts
     * use scale=4 HALF_UP — matches {@code Invoice.amount}'s column precision.
     */
    private BigDecimal computeDiscount(Coupon coupon, BigDecimal subtotal) {
        if (coupon.getType() == DiscountType.FIXED) {
            BigDecimal flat = coupon.getDiscount() == null ? BigDecimal.ZERO : coupon.getDiscount();
            return flat.compareTo(subtotal) > 0 ? subtotal : flat;
        }
        if (coupon.getType() == DiscountType.PERCENT && coupon.getPercentage() != null) {
            BigDecimal ratio = coupon.getPercentage().divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP);
            return subtotal.multiply(ratio).setScale(4, RoundingMode.HALF_UP);
        }
        return BigDecimal.ZERO;
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
