package com.lamb.springaiknowledgeserver.auth;

import com.lamb.springaiknowledgeserver.entity.User;
import com.lamb.springaiknowledgeserver.entity.UserRole;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import lombok.Getter;
import org.jspecify.annotations.NonNull;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public class UserPrincipal implements UserDetails {

    @Getter
    private final long id;
    private final String username;
    private final String password;
    private final UserRole role;

    private UserPrincipal(long id, String username, String password, UserRole role) {
        this.id = id;
        this.username = username;
        this.password = password;
        this.role = role;
    }

    public static UserPrincipal from(User user) {
        Long id = Objects.requireNonNull(user.getId(), "User id must not be null");
        return new UserPrincipal(
            id,
            user.getUsername(),
            user.getPasswordHash(),
            user.getRole()
        );
    }

    @Override
    public @NonNull Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public @NonNull String getUsername() {
        return username;
    }

    @Override
    public @NonNull String getPassword() {
        return password;
    }
}
