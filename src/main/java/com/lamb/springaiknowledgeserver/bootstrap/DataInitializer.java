package com.lamb.springaiknowledgeserver.bootstrap;

import com.lamb.springaiknowledgeserver.entity.Permission;
import com.lamb.springaiknowledgeserver.entity.Role;
import com.lamb.springaiknowledgeserver.entity.User;
import com.lamb.springaiknowledgeserver.repository.RoleRepository;
import com.lamb.springaiknowledgeserver.repository.UserRepository;
import java.util.EnumSet;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DataInitializer implements ApplicationRunner {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.admin.username}")
    private String adminUsername;

    @Value("${app.admin.password}")
    private String adminPassword;

    @Override
    public void run(@NonNull ApplicationArguments args) {
        Role adminRole = ensureRole(
            "ADMIN",
            EnumSet.of(
                Permission.USER_READ,
                Permission.USER_WRITE,
                Permission.ROLE_READ,
                Permission.ROLE_WRITE,
                Permission.DOC_READ,
                Permission.DOC_WRITE
            )
        );
        ensureRole("USER", EnumSet.of(Permission.DOC_READ));

        User admin = userRepository.findByUsername(adminUsername).orElse(null);
        if (admin == null) {
            admin = new User();
            admin.setUsername(adminUsername);
            admin.setPasswordHash(passwordEncoder.encode(adminPassword));
            admin.setRole(adminRole);
            userRepository.save(admin);
        } else if (admin.getRole() == null) {
            admin.setRole(adminRole);
            userRepository.save(admin);
        }
    }

    private Role ensureRole(String name, EnumSet<Permission> permissions) {
        return roleRepository.findByName(name)
            .orElseGet(() -> {
                Role role = new Role();
                role.setName(name);
                role.setPermissions(permissions);
                return roleRepository.save(role);
            });
    }
}
