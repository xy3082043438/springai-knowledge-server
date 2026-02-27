package com.lamb.springaiknowledgeserver.auth;

import com.lamb.springaiknowledgeserver.entity.User;
import com.lamb.springaiknowledgeserver.entity.Permission;
import com.lamb.springaiknowledgeserver.entity.Role;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
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
    private final List<GrantedAuthority> authorities;

    private UserPrincipal(long id, String username, String password, List<GrantedAuthority> authorities) {
        this.id = id;
        this.username = username;
        this.password = password;
        this.authorities = authorities;
    }

    public static UserPrincipal from(User user) {
        Long id = Objects.requireNonNull(user.getId(), "User id must not be null");
        List<GrantedAuthority> authorities = buildAuthorities(user.getRole());
        return new UserPrincipal(
            id,
            user.getUsername(),
            user.getPasswordHash(),
            authorities
        );
    }

    @Override
    public @NonNull Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
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

    private static List<GrantedAuthority> buildAuthorities(Role role) {
        if (role == null) {
            return List.of();
        }
        List<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_" + role.getName()));
        if (role.getPermissions() != null) {
            for (Permission permission : role.getPermissions()) {
                authorities.add(new SimpleGrantedAuthority(permission.name()));
            }
        }
        return Collections.unmodifiableList(authorities);
    }
}
