package com.humano.web.rest.payroll;

import com.humano.domain.enumeration.payroll.Kind;
import com.humano.domain.enumeration.payroll.PayComponentCode;
import com.humano.dto.payroll.request.CreatePayComponentRequest;
import com.humano.dto.payroll.response.PayComponentResponse;
import com.humano.security.annotation.RequirePayrollAdmin;
import com.humano.service.payroll.PayComponentService;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import tech.jhipster.web.util.PaginationUtil;

/**
 * Pay components &mdash; reusable earning/deduction/employer-charge buckets.
 * Their executable rules live on {@link PayRuleResource}.
 */
@RestController
@RequestMapping("/api/payroll/pay-components")
@RequirePayrollAdmin
public class PayComponentResource {

    private final PayComponentService payComponentService;

    public PayComponentResource(PayComponentService payComponentService) {
        this.payComponentService = payComponentService;
    }

    @PostMapping
    public ResponseEntity<PayComponentResponse> create(@Valid @RequestBody CreatePayComponentRequest request) {
        PayComponentResponse result = payComponentService.createComponent(request);
        return ResponseEntity.created(URI.create("/api/payroll/pay-components/" + result.id())).body(result);
    }

    @GetMapping
    public ResponseEntity<Page<PayComponentResponse>> list(Pageable pageable) {
        Page<PayComponentResponse> page = payComponentService.getAllComponents(pageable);
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), page);
        return ResponseEntity.ok().headers(headers).body(page);
    }

    @GetMapping("/by-kind/{kind}")
    public ResponseEntity<List<PayComponentResponse>> byKind(@PathVariable Kind kind) {
        return ResponseEntity.ok(payComponentService.getComponentsByKind(kind));
    }

    @GetMapping("/by-code/{code}")
    public ResponseEntity<PayComponentResponse> byCode(@PathVariable PayComponentCode code) {
        return ResponseEntity.ok(payComponentService.getComponentByCode(code));
    }

    public record UpdateComponentRequest(String name, Boolean taxable, Boolean contributesToSocial, Integer calcPhase) {}

    @PutMapping("/{id}")
    public ResponseEntity<PayComponentResponse> update(@PathVariable UUID id, @RequestBody UpdateComponentRequest body) {
        return ResponseEntity.ok(
            payComponentService.updateComponent(id, body.name(), body.taxable(), body.contributesToSocial(), body.calcPhase())
        );
    }

    @GetMapping("/{id}/active-rules")
    public ResponseEntity<List<PayComponentResponse.PayRuleSummary>> activeRules(
        @PathVariable UUID id,
        @RequestParam(required = false) LocalDate asOfDate
    ) {
        return ResponseEntity.ok(payComponentService.getActiveRules(id, asOfDate));
    }

    @GetMapping("/calculation-order")
    public ResponseEntity<List<PayComponentResponse>> calculationOrder() {
        return ResponseEntity.ok(payComponentService.getCalculationOrder());
    }

    @PostMapping("/{sourceId}/copy-rules-to/{targetId}")
    public ResponseEntity<PayComponentResponse> copyRules(@PathVariable UUID sourceId, @PathVariable UUID targetId) {
        return ResponseEntity.ok(payComponentService.copyRules(sourceId, targetId));
    }

    @GetMapping("/statistics")
    public ResponseEntity<Map<String, Object>> statistics() {
        return ResponseEntity.ok(payComponentService.getComponentStatistics());
    }
}
