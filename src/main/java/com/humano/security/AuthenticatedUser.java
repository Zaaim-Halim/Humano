package com.humano.security;

import com.humano.domain.shared.Authority;
import com.humano.domain.shared.User;
import java.util.Collection;
import java.util.UUID;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/**
 * @author halimzaaim
 */
public class AuthenticatedUser extends org.springframework.security.core.userdetails.User {

    private final UUID id;

    //TODO add more fields if needed
    public AuthenticatedUser(String username, String password, Collection<? extends GrantedAuthority> authorities, final UUID id) {
        super(username, password, authorities);
        this.id = id;
    }

    public static AuthenticatedUser fromUser(User user) {
        return new AuthenticatedUser(
            user.getLogin(),
            user.getPassword(),
            user.getAuthorities().stream().map(Authority::getName).map(SimpleGrantedAuthority::new).toList(),
            user.getId()
        );
    }

    public UUID getId() {
        return id;
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
