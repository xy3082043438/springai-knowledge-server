package com.lamb.springaiknowledgeserver.controller;

import com.lamb.springaiknowledgeserver.auth.UserPrincipal;
import com.lamb.springaiknowledgeserver.dto.MeUpdateRequest;
import com.lamb.springaiknowledgeserver.dto.UserCreateRequest;
import com.lamb.springaiknowledgeserver.dto.UserResponse;
import com.lamb.springaiknowledgeserver.dto.UserUpdateRequest;
import com.lamb.springaiknowledgeserver.entity.User;
import com.lamb.springaiknowledgeserver.service.UserService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal UserPrincipal principal) {
        User user = userService.getById(principal.getId());
        return UserResponse.from(user);
    }

    @PatchMapping("/me")
    public UserResponse updateMe(
        @AuthenticationPrincipal UserPrincipal principal,
        @Valid @RequestBody MeUpdateRequest request
    ) {
        User user = userService.updateMe(principal.getId(), request);
        return UserResponse.from(user);
    }

    @PreAuthorize("hasAuthority('USER_READ')")
    @GetMapping
    public List<UserResponse> list() {
        return userService.listUsers().stream()
            .map(UserResponse::from)
            .toList();
    }

    @PreAuthorize("hasAuthority('USER_WRITE')")
    @PostMapping
    public UserResponse create(@Valid @RequestBody UserCreateRequest request) {
        return UserResponse.from(userService.createUser(request));
    }

    @PreAuthorize("hasAuthority('USER_WRITE')")
    @PatchMapping("/{id}")
    public UserResponse update(
        @PathVariable Long id,
        @Valid @RequestBody UserUpdateRequest request
    ) {
        return UserResponse.from(userService.updateUser(id, request));
    }
}
