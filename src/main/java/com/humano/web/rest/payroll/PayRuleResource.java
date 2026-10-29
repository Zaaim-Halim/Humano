package com.humano.web.rest.payroll;

import com.humano.dto.payroll.request.CreatePayRuleRequest;
import com.humano.dto.payroll.response.FormulaMetadataResponse;
import com.humano.dto.payroll.response.PayComponentResponse;
import com.humano.security.PermissionsConstants;
import com.humano.security.annotation.RequireFeature;
import com.humano.security.annotation.RequirePermission;
import com.humano.service.billing.entitlement.FeatureCodes;
import com.humano.service.payroll.PayComponentService;
import com.humano.service.payroll.template.PayrollTemplateService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Pay rules . Rules are owned by their pay component; this resource exposes the
 * rule-only operations from {@link PayComponentService} under a dedicated path.
 */
@RequireFeature(FeatureCodes.PAYROLL)
@RestController
@RequestMapping("/api/payroll/pay-rules")
@RequirePermission(PermissionsConstants.MANAGE_PAY_COMPONENTS)
public class PayRuleResource {

    private final PayComponentService payComponentService;
    private final PayrollTemplateService templateService;

    public PayRuleResource(PayComponentService payComponentService, PayrollTemplateService templateService) {
        this.payComponentService = payComponentService;
        this.templateService = templateService;
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

    /**
     * Engine contract (functions, variables, constants, limits) for building a guided
     * formula editor. Static per deployment, so the client may cache it.
     */
    @GetMapping("/formula-metadata")
    public ResponseEntity<FormulaMetadataResponse> formulaMetadata() {
        return ResponseEntity.ok(payComponentService.getFormulaMetadata());
    }

    /**
     * The payroll templates switched on: for their countries' employees they compute income tax and
     * employer contributions in place of rules here. Readable with this screen's permission, which
     * doesn't open the template catalog.
     */
    @GetMapping("/templates-in-use")
    public ResponseEntity<List<PayrollTemplateService.TemplateInUse>> templatesInUse() {
        return ResponseEntity.ok(templateService.inUse());
    }
}
