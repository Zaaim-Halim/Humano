package com.humano.security;

import com.humano.domain.shared.Authority;
import com.humano.domain.shared.User;
import com.humano.tenancy.TenantContext;
import java.util.Collection;
import java.util.UUID;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/**
 * The signed-in person, bound to the tenant they signed in to. {@code TenantResolutionFilter} refuses
 * any request for another tenant, whichever way this principal was created (sign-in or remember-me).
 *
 * @author halimzaaim
 */
public class AuthenticatedUser extends org.springframework.security.core.userdetails.User {

    private final UUID id;

    private final String tenant;

    //TODO add more fields if needed
    public AuthenticatedUser(
        String username,
        String password,
        Collection<? extends GrantedAuthority> authorities,
        final UUID id,
        final String tenant
    ) {
        super(username, password, authorities);
        this.id = id;
        this.tenant = tenant;
    }

    public static AuthenticatedUser fromUser(User user) {
        return new AuthenticatedUser(
            user.getLogin(),
            user.getPassword(),
            user.getAuthorities().stream().map(Authority::getName).map(SimpleGrantedAuthority::new).toList(),
            user.getId(),
            TenantContext.getCurrentTenant()
        );
    }

    public UUID getId() {
        return id;
    }

    public String getTenant() {
        return tenant;
    }

    @Override
    public boolean equals(Object obj) {
        return super.equals(obj);
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }
}
