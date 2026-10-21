package com.humano.service.signup;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.humano.domain.enumeration.CountryCode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import tech.jhipster.config.JHipsterProperties;

class SignupLinkTest {

    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final JHipsterProperties properties = new JHipsterProperties();
    private SignupLink link;

    SignupLinkTest() {
        properties.getSecurity().getRememberMe().setKey("test-key");
        link = new SignupLink(mapper, properties);
    }

    @Test
    void theChosenPayrollTemplateTravelsInTheLink() {
        UUID plan = UUID.randomUUID();
        Instant expires = Instant.now().plusSeconds(3600);
        String token = link.issue(
            new SignupLink.Payload("a@acme.test", "Acme", "acme", CountryCode.DE, "Europe/Berlin", plan, "de-employment", expires)
        );
        SignupLink.Payload read = link.read(token);
        assertThat(read.payrollTemplateId()).isEqualTo("de-employment");
        assertThat(read.planId()).isEqualTo(plan);
    }

    @Test
    void aLinkIssuedBeforeTemplatesExistedStillReads() throws Exception {
        // The JSON of a link emailed before the payrollTemplateId field was added, signed as SignupLink signs.
        String json =
            "{\"email\":\"a@acme.test\",\"companyName\":\"Acme\",\"subdomain\":\"acme\",\"country\":\"NL\",\"timezone\":\"UTC\"," +
            "\"planId\":\"" +
            UUID.randomUUID() +
            "\",\"expiresAt\":\"" +
            Instant.now().plusSeconds(3600) +
            "\"}";
        String body = Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec("signup:test-key".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String signature = Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(body.getBytes(StandardCharsets.US_ASCII)));

        SignupLink.Payload read = link.read(body + "." + signature);

        assertThat(read.country()).isEqualTo(CountryCode.NL);
        assertThat(read.payrollTemplateId()).isNull();
        assertThat(read.expired()).isFalse();
    }
}
