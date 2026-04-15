package com.lamb.springaiknowledgeserver.modules.system.role;

import com.lamb.springaiknowledgeserver.modules.system.role.PermissionOptionResponse;
import com.lamb.springaiknowledgeserver.modules.system.role.RoleCreateRequest;
import com.lamb.springaiknowledgeserver.modules.system.role.RoleResponse;
import com.lamb.springaiknowledgeserver.modules.system.role.RoleUpdateRequest;
import com.lamb.springaiknowledgeserver.modules.system.role.Permission;
import com.lamb.springaiknowledgeserver.modules.system.role.Role;
import com.lamb.springaiknowledgeserver.modules.knowledge.document.DocumentRepository;
import com.lamb.springaiknowledgeserver.modules.system.role.RoleRepository;
import com.lamb.springaiknowledgeserver.modules.system.user.UserRepository;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class RoleService {

    private static final Set<String> SYSTEM_ROLE_NAMES = Set.of("ADMIN", "USER");

    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final DocumentRepository documentRepository;

    public List<Role> listRoles() {
        return roleRepository.findAll(Sort.by(Sort.Direction.ASC, "id"));
    }

    public List<RoleResponse> listRoleResponses() {
        return listRoles().stream()
            .map(this::toResponse)
            .toList();
    }

    public List<PermissionOptionResponse> listPermissionOptions() {
        return Arrays.stream(Permission.values())
            .map(this::toPermissionOption)
            .toList();
    }

    public Role getById(Long id) {
        return roleRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "无法完成操作，您指定的角色似乎不存在"));
    }

    public RoleResponse getResponseById(Long id) {
        return toResponse(getById(id));
    }

    public Role create(RoleCreateRequest request) {
        String roleName = normalizeRoleName(request.getName());
        if (roleRepository.existsByName(roleName)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "该角色名称已存在，请选择一个不同的名称");
        }
        Role role = new Role();
        role.setName(roleName);
        role.setPermissions(normalizePermissions(request.getPermissions()));
        return roleRepository.save(role);
    }

    public Role update(Long id, RoleUpdateRequest request) {
        Role role = getById(id);
        if (request.getName() != null) {
            String roleName = normalizeRoleName(request.getName());
            if (isSystemRole(role.getName()) && !roleName.equals(role.getName())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "抱歉，系统内置角色的名称受保护，无法修改");
            }
            if (!roleName.equals(role.getName()) && roleRepository.existsByName(roleName)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "该角色名称已存在，请选择一个不同的名称");
            }
            role.setName(roleName);
        }
        if (request.getPermissions() != null) {
            role.setPermissions(normalizePermissions(request.getPermissions()));
        }
        return roleRepository.save(role);
    }

    public void delete(Long id) {
        Role role = getById(id);
        if (isSystemRole(role.getName())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "抱歉，系统内置角色受到保护，无法删除");
        }
        long userCount = userRepository.countByRole_Id(id);
        if (userCount > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "无法删除角色，该角色下还有关联用户，请先移除");
        }
        long documentCount = documentRepository.countByAllowedRoleId(id);
        if (documentCount > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "无法删除角色，该角色下还有关联文档，请先移除");
        }
        roleRepository.delete(role);
    }

    public RoleResponse toResponse(Role role) {
        Long roleId = role.getId();
        if (roleId == null) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "角色数据出现异常，请联系管理员处理");
        }
        long userCount = userRepository.countByRole_Id(roleId);
        long documentCount = documentRepository.countByAllowedRoleId(roleId);
        return RoleResponse.from(role, userCount, documentCount, isSystemRole(role.getName()));
    }

    private Set<Permission> normalizePermissions(Set<Permission> permissions) {
        if (permissions == null || permissions.isEmpty()) {
            return EnumSet.noneOf(Permission.class);
        }
        return EnumSet.copyOf(permissions);
    }

    private String normalizeRoleName(String roleName) {
        if (roleName == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "角色名称不能为空，请填写");
        }
        String normalized = roleName.trim();
        if (normalized.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "角色名称不能为空，请填写后重试");
        }
        return normalized;
    }

    private boolean isSystemRole(String roleName) {
        return roleName != null && SYSTEM_ROLE_NAMES.contains(roleName);
    }

    private PermissionOptionResponse toPermissionOption(Permission permission) {
        return switch (permission) {
            case USER_READ -> new PermissionOptionResponse(
                permission.name(), "USER", "用户", "查看用户", "允许查看用户列表和详情"
            );
            case USER_WRITE -> new PermissionOptionResponse(
                permission.name(), "USER", "用户", "管理用户", "允许新增、修改和删除用户"
            );
            case ROLE_READ -> new PermissionOptionResponse(
                permission.name(), "ROLE", "角色", "查看角色", "允许查看角色详情、权限清单和使用情况"
            );
            case ROLE_WRITE -> new PermissionOptionResponse(
                permission.name(), "ROLE", "角色", "管理角色", "允许新增、修改、删除角色并分配权限"
            );
            case DOC_READ -> new PermissionOptionResponse(
                permission.name(), "DOC", "文档", "查看文档", "允许查看知识库文档并执行问答"
            );
            case DOC_WRITE -> new PermissionOptionResponse(
                permission.name(), "DOC", "文档", "管理文档", "允许上传、编辑、删除文档和重建索引"
            );
            case CONFIG_READ -> new PermissionOptionResponse(
                permission.name(), "CONFIG", "配置", "查看配置", "允许查看系统配置项"
            );
            case CONFIG_WRITE -> new PermissionOptionResponse(
                permission.name(), "CONFIG", "配置", "管理配置", "允许修改并刷新系统配置项"
            );
            case LOG_READ -> new PermissionOptionResponse(
                permission.name(), "LOG", "日志", "查看日志", "允许查看操作日志、问答日志和数据大屏"
            );
            case LOG_WRITE -> new PermissionOptionResponse(
                permission.name(), "LOG", "日志", "管理日志", "允许执行日志维护类操作"
            );
            case FEEDBACK_READ -> new PermissionOptionResponse(
                permission.name(), "FEEDBACK", "反馈", "查看反馈", "允许查看问答反馈记录"
            );
            case FEEDBACK_WRITE -> new PermissionOptionResponse(
                permission.name(), "FEEDBACK", "反馈", "处理反馈", "允许提交和处理问答反馈"
            );
        };
    }
}



