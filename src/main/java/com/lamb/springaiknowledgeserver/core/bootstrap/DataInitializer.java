package com.lamb.springaiknowledgeserver.core.bootstrap;

import com.lamb.springaiknowledgeserver.modules.system.role.Permission;
import com.lamb.springaiknowledgeserver.modules.system.role.Role;
import com.lamb.springaiknowledgeserver.modules.system.user.User;
import com.lamb.springaiknowledgeserver.modules.system.role.RoleRepository;
import com.lamb.springaiknowledgeserver.modules.system.user.UserRepository;
import java.util.EnumSet;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.core.annotation.Order;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;

@Component
@Order(10)
@Slf4j
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
            EnumSet.allOf(Permission.class)
        );
        log.info("[系统配置] ADMIN 角色初始化完成，当前权限标识: {}", adminRole.getPermissions());
        ensureRole("USER", EnumSet.of(
            Permission.DOC_READ,
            Permission.FEEDBACK_WRITE,
            Permission.QA_READ,
            Permission.DASHBOARD_READ
        ));

        User admin = userRepository.findByUsername(adminUsername).orElse(null);
        if (admin == null) {
            admin = new User();
            admin.setUsername(adminUsername);
            admin.setPasswordHash(passwordEncoder.encode(adminPassword));
            admin.setRole(adminRole);
            userRepository.save(admin);
        } else {
            boolean changed = false;
            if (admin.getRole() == null) {
                admin.setRole(adminRole);
                changed = true;
            }
            if (!admin.isEnabled()) {
                admin.setEnabled(true);
                changed = true;
            }
            if (changed) {
                userRepository.save(admin);
            }
        }
    }

    private Role ensureRole(String name, EnumSet<Permission> permissions) {
        Role role = roleRepository.findByName(name).orElse(null);
        if (role == null) {
            role = new Role();
            role.setName(name);
            role.setPermissions(permissions);
            return roleRepository.save(role);
        }
        boolean needsUpdate = role.getPermissions() == null || !role.getPermissions().containsAll(permissions);
        if (needsUpdate) {
            EnumSet<Permission> merged = EnumSet.noneOf(Permission.class);
            if (role.getPermissions() != null && !role.getPermissions().isEmpty()) {
                merged.addAll(role.getPermissions());
            }
            merged.addAll(permissions);
            role.setPermissions(merged);
            return roleRepository.save(role);
        }
        return role;
    }
}


