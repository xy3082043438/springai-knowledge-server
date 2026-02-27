package com.lamb.springaiknowledgeserver.service;

import com.lamb.springaiknowledgeserver.dto.RoleCreateRequest;
import com.lamb.springaiknowledgeserver.dto.RoleUpdateRequest;
import com.lamb.springaiknowledgeserver.entity.Permission;
import com.lamb.springaiknowledgeserver.entity.Role;
import com.lamb.springaiknowledgeserver.repository.RoleRepository;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class RoleService {

    private final RoleRepository roleRepository;

    public List<Role> listRoles() {
        return roleRepository.findAll();
    }

    public Role getById(Long id) {
        return roleRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Role not found"));
    }

    public Role create(RoleCreateRequest request) {
        if (roleRepository.existsByName(request.getName())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Role already exists");
        }
        Role role = new Role();
        role.setName(request.getName());
        role.setPermissions(normalizePermissions(request.getPermissions()));
        return roleRepository.save(role);
    }

    public Role update(Long id, RoleUpdateRequest request) {
        Role role = getById(id);
        if (request.getName() != null && !request.getName().isBlank()) {
            if (!request.getName().equals(role.getName()) && roleRepository.existsByName(request.getName())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Role already exists");
            }
            role.setName(request.getName());
        }
        if (request.getPermissions() != null) {
            role.setPermissions(normalizePermissions(request.getPermissions()));
        }
        return roleRepository.save(role);
    }

    private Set<Permission> normalizePermissions(Set<Permission> permissions) {
        if (permissions == null || permissions.isEmpty()) {
            return EnumSet.noneOf(Permission.class);
        }
        return EnumSet.copyOf(permissions);
    }
}
