package com.humano.dto.tenant;

import com.humano.domain.billing.SubscriptionPlan;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * DTO for tenant registration requests.
 *
 * @author Humano Team
 */
public class TenantRegistrationDTO {

    @NotBlank(message = "Company name is required")
    @Size(min = 2, max = 100, message = "Company name must be between 2 and 100 characters")
    private String companyName;

    @NotBlank(message = "Domain is required")
    @Pattern(regexp = "^[a-zA-Z0-9][a-zA-Z0-9-]{1,61}[a-zA-Z0-9]\\.[a-zA-Z]{2,}$", message = "Domain must be a valid domain name")
    private String domain;

    @NotBlank(message = "Subdomain is required")
    @Pattern(
        regexp = "^[a-zA-Z0-9]([a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?$",
        message = "Subdomain must contain only letters, numbers, and hyphens"
    )
    @Size(min = 2, max = 63, message = "Subdomain must be between 2 and 63 characters")
    private String subdomain;

    private String timezone;

    private SubscriptionPlan subscriptionPlan;

    private String preferredRegion;

    private boolean requestDedicatedServer;

    // Admin user details
    @NotBlank(message = "Admin email is required")
    private String adminEmail;

    @NotBlank(message = "Admin first name is required")
    private String adminFirstName;

    @NotBlank(message = "Admin last name is required")
    private String adminLastName;

    private String adminPassword;

    public TenantRegistrationDTO() {}

    public String getCompanyName() {
        return companyName;
    }

    public void setCompanyName(String companyName) {
        this.companyName = companyName;
    }

    public String getDomain() {
        return domain;
    }

    public void setDomain(String domain) {
        this.domain = domain;
    }

    public String getSubdomain() {
        return subdomain;
    }

    public void setSubdomain(String subdomain) {
        this.subdomain = subdomain;
    }

    public String getTimezone() {
        return timezone;
    }

    public void setTimezone(String timezone) {
        this.timezone = timezone;
    }

    public SubscriptionPlan getSubscriptionPlan() {
        return subscriptionPlan;
    }

    public void setSubscriptionPlan(SubscriptionPlan subscriptionPlan) {
        this.subscriptionPlan = subscriptionPlan;
    }

    public String getPreferredRegion() {
        return preferredRegion;
    }

    public void setPreferredRegion(String preferredRegion) {
        this.preferredRegion = preferredRegion;
    }

    public boolean isRequestDedicatedServer() {
        return requestDedicatedServer;
    }

    public void setRequestDedicatedServer(boolean requestDedicatedServer) {
        this.requestDedicatedServer = requestDedicatedServer;
    }

    public String getAdminEmail() {
        return adminEmail;
    }

    public void setAdminEmail(String adminEmail) {
        this.adminEmail = adminEmail;
    }

    public String getAdminFirstName() {
        return adminFirstName;
    }

    public void setAdminFirstName(String adminFirstName) {
        this.adminFirstName = adminFirstName;
    }

    public String getAdminLastName() {
        return adminLastName;
    }

    public void setAdminLastName(String adminLastName) {
        this.adminLastName = adminLastName;
    }

    public String getAdminPassword() {
        return adminPassword;
    }

    public void setAdminPassword(String adminPassword) {
        this.adminPassword = adminPassword;
    }

    @Override
    public String toString() {
        return (
            "TenantRegistrationDTO{" +
            "companyName='" +
            companyName +
            '\'' +
            ", domain='" +
            domain +
            '\'' +
            ", subdomain='" +
            subdomain +
            '\'' +
            ", timezone='" +
            timezone +
            '\'' +
            ", preferredRegion='" +
            preferredRegion +
            '\'' +
            ", requestDedicatedServer=" +
            requestDedicatedServer +
            ", adminEmail='" +
            adminEmail +
            '\'' +
            '}'
        );
    }
}
