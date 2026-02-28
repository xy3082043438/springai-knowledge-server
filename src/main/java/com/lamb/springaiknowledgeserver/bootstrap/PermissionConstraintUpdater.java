package com.lamb.springaiknowledgeserver.bootstrap;

import com.lamb.springaiknowledgeserver.entity.Permission;
import java.util.Arrays;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@Order(0)
@RequiredArgsConstructor
public class PermissionConstraintUpdater implements ApplicationRunner {

    private static final String TABLE_NAME = "app_role_permission";
    private static final String CONSTRAINT_NAME = "app_role_permission_permission_check";
    private static final Logger log = LoggerFactory.getLogger(PermissionConstraintUpdater.class);

    private final JdbcTemplate jdbcTemplate;

    @Override
    @SuppressWarnings("SqlSourceToSinkFlow")
    public void run(@NonNull ApplicationArguments args) {
        if (!tableExists()) {
            return;
        }
        String values = Arrays.stream(Permission.values())
            .map(permission -> "'" + permission.name() + "'")
            .collect(Collectors.joining(","));
        try {
            jdbcTemplate.execute("ALTER TABLE " + TABLE_NAME + " DROP CONSTRAINT IF EXISTS " + CONSTRAINT_NAME);
            String addConstraintSql = "ALTER TABLE " + TABLE_NAME + " ADD CONSTRAINT " + CONSTRAINT_NAME
                + " CHECK (permission IN (" + values + "))";
            jdbcTemplate.execute(addConstraintSql);
        } catch (Exception ex) {
            // Avoid blocking startup on constraint update.
            log.debug("Failed to update permission constraint", ex);
        }
    }

    private boolean tableExists() {
        try {
            String exists = jdbcTemplate.queryForObject(
                "SELECT to_regclass(?)",
                String.class,
                "public." + TABLE_NAME
            );
            return exists != null && !exists.isBlank();
        } catch (Exception ex) {
            log.debug("Failed to check permission table existence", ex);
            return false;
        }
    }
}
