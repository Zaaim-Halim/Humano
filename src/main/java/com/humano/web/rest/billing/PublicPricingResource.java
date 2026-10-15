package com.humano.web.rest.billing;

import com.humano.dto.billing.responses.PublicPricingResponse;
import com.humano.service.billing.PublicPricingService;
import java.time.Duration;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The public pricing page (marketing site, no sign-in): listed plans and what each includes. */
@RestController
@RequestMapping("/api/public/pricing")
public class PublicPricingResource {

    private final PublicPricingService pricingService;

    public PublicPricingResource(PublicPricingService pricingService) {
        this.pricingService = pricingService;
    }

    @GetMapping
    public ResponseEntity<PublicPricingResponse> pricing() {
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePublic()).body(pricingService.pricing());
    }
}
