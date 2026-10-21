package com.humano.web.rest.payroll;

import com.humano.domain.enumeration.CountryCode;
import com.humano.dto.payroll.response.PublicPayrollTemplateResponse;
import com.humano.service.payroll.template.PayrollTemplateSuggestions;
import java.time.Duration;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The sign-up form's payroll template suggestions (no sign-in): the templates a workspace in a country can start with. */
@RestController
@RequestMapping("/api/public/payroll-templates")
public class PublicPayrollTemplateResource {

    private final PayrollTemplateSuggestions suggestions;

    public PublicPayrollTemplateResource(PayrollTemplateSuggestions suggestions) {
        this.suggestions = suggestions;
    }

    @GetMapping
    public ResponseEntity<List<PublicPayrollTemplateResponse>> forCountry(@RequestParam CountryCode country) {
        return ResponseEntity.ok()
            .cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePublic())
            .body(suggestions.forCountry(country));
    }
}
