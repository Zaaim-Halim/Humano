package com.humano.web.rest.demo;

import com.humano.dto.demo.SubmitDemoRequest;
import com.humano.service.demo.DemoRequestService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The public site's "Book a demo" form (no sign-in). Always 202: it never says what it did with a request. */
@RestController
@RequestMapping("/api/public/demo-requests")
public class PublicDemoRequestResource {

    private final DemoRequestService requestService;

    public PublicDemoRequestResource(DemoRequestService requestService) {
        this.requestService = requestService;
    }

    @PostMapping
    public ResponseEntity<Void> submit(@Valid @RequestBody SubmitDemoRequest request) {
        requestService.submit(request);
        return ResponseEntity.accepted().build();
    }
}
