package com.humano.web.rest.payroll;

import com.humano.dto.payroll.request.CreatePayRuleRequest;
import com.humano.dto.payroll.response.PayComponentResponse;
import com.humano.security.AuthoritiesConstants;
import com.humano.service.payroll.PayComponentService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Pay rules (P2.5). Rules are owned by their pay component; this resource exposes the
 * rule-only operations from {@link PayComponentService} under a dedicated path.
 */
@RestController
@RequestMapping("/api/payroll/pay-rules")
@PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.ADMIN + "', '" + AuthoritiesConstants.PAYROLL_ADMIN + "')")
public class PayRuleResource {

    private final PayComponentService payComponentService;

    public PayRuleResource(PayComponentService payComponentService) {
        this.payComponentService = payComponentService;
    }

    @PostMapping
    public ResponseEntity<PayComponentResponse> create(@Valid @RequestBody CreatePayRuleRequest request) {
        PayComponentResponse parent = payComponentService.createRule(request);
        return ResponseEntity.created(URI.create("/api/payroll/pay-components/" + parent.id())).body(parent);
    }

    public record UpdateRuleFormulaRequest(String formula) {}

    @PutMapping("/{ruleId}/formula")
    public ResponseEntity<PayComponentResponse> updateFormula(@PathVariable UUID ruleId, @RequestBody UpdateRuleFormulaRequest body) {
        return ResponseEntity.ok(payComponentService.updateRuleFormula(ruleId, body.formula()));
    }

    @PostMapping("/{ruleId}/active")
    public ResponseEntity<PayComponentResponse> setActive(@PathVariable UUID ruleId, @RequestParam boolean active) {
        return ResponseEntity.ok(payComponentService.setRuleActive(ruleId, active));
    }

    @PostMapping("/validate-formula")
    public ResponseEntity<Map<String, Object>> validateFormula(@RequestBody UpdateRuleFormulaRequest body) {
        return ResponseEntity.ok(payComponentService.validateFormula(body.formula()));
    }
}
