package com.humano.web.rest.legal;

import com.humano.dto.platform.compliance.PublicRetention;
import com.humano.service.platform.ComplianceService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Figures the public legal pages and the website quote, set in the platform console. No sign-in needed. */
@RestController
@RequestMapping("/api/public/legal")
public class PublicLegalResource {

    private final ComplianceService complianceService;

    public PublicLegalResource(ComplianceService complianceService) {
        this.complianceService = complianceService;
    }

    /** How long a workspace is kept after its subscription ends, and how long before deletion its admin is warned. */
    @GetMapping("/retention")
    public PublicRetention retention() {
        return complianceService.publicRetention();
    }
}
