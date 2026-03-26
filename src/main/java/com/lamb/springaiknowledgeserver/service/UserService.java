package com.lamb.springaiknowledgeserver.service;

import com.lamb.springaiknowledgeserver.dto.MeUpdateRequest;
import com.lamb.springaiknowledgeserver.dto.UserCreateRequest;
import com.lamb.springaiknowledgeserver.dto.UserUpdateRequest;
import com.lamb.springaiknowledgeserver.entity.Role;
import com.lamb.springaiknowledgeserver.entity.User;
import com.lamb.springaiknowledgeserver.repository.RoleRepository;
import com.lamb.springaiknowledgeserver.repository.UserRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    public List<User> listUsers() {
        return userRepository.findAll();
    }

    public User getById(Long id) {
        return userRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }

    public User createUser(UserCreateRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username already exists");
        }
        Role role = resolveRole(request.getRole());
        User user = new User();
        user.setUsername(request.getUsername());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setRole(role);
        return userRepository.save(user);
    }

    public User updateUser(Long id, UserUpdateRequest request) {
        User user = getById(id);
        if (request.getUsername() != null && !request.getUsername().isBlank()) {
            if (!request.getUsername().equals(user.getUsername())
                && userRepository.existsByUsername(request.getUsername())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Username already exists");
            }
            user.setUsername(request.getUsername());
        }
        if (request.getRole() != null && !request.getRole().isBlank()) {
            user.setRole(resolveRole(request.getRole()));
        }
        return userRepository.save(user);
    }

    public User updateMe(Long id, MeUpdateRequest request) {
        User user = getById(id);
        if (request.getUsername() != null && !request.getUsername().isBlank()) {
            if (!request.getUsername().equals(user.getUsername())
                && userRepository.existsByUsername(request.getUsername())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Username already exists");
            }
            user.setUsername(request.getUsername());
        }
        return userRepository.save(user);
    }

    public void deleteUser(Long id, Long currentUserId) {
        if (id != null && id.equals(currentUserId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Current user cannot be deleted");
        }
        User user = getById(id);
        userRepository.delete(user);
    }

    private Role resolveRole(String roleName) {
        String effectiveName = (roleName == null || roleName.isBlank()) ? "USER" : roleName;
        return roleRepository.findByName(effectiveName)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Role not found"));
    }
}
