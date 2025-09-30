package com.humano.repository.billing;

import com.humano.domain.billing.Coupon;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.UUID;
import java.util.List;

/**
 * Spring Data JPA repository for the {@link Coupon} entity.
 */
@Repository
public interface CouponRepository extends JpaRepository<Coupon, UUID>, JpaSpecificationExecutor<Coupon> {
    List<Coupon> findByCodeContainingIgnoreCase(String code);
}
