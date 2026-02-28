package com.lamb.springaiknowledgeserver.controller;

import com.lamb.springaiknowledgeserver.auth.UserPrincipal;
import com.lamb.springaiknowledgeserver.dto.MeUpdateRequest;
import com.lamb.springaiknowledgeserver.dto.UserCreateRequest;
import com.lamb.springaiknowledgeserver.dto.UserResponse;
import com.lamb.springaiknowledgeserver.dto.UserUpdateRequest;
import com.lamb.springaiknowledgeserver.entity.User;
import com.lamb.springaiknowledgeserver.service.OperationLogService;
import com.lamb.springaiknowledgeserver.service.UserService;
import com.lamb.springaiknowledgeserver.util.RequestUtils;
import jakarta.servlet.http.HttpServletRequest;
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
    private final OperationLogService operationLogService;

    @GetMapping("/me")
    public UserResponse me(
        @AuthenticationPrincipal UserPrincipal principal,
        HttpServletRequest httpRequest
    ) {
        User user = userService.getById(principal.getId());
        operationLogService.log(
            principal.getId(),
            principal.getUsername(),
            "USER_ME_VIEW",
            "USER",
            String.valueOf(user.getId()),
            "me",
            RequestUtils.resolveClientIp(httpRequest),
            true
        );
        return UserResponse.from(user);
    }

    @PatchMapping("/me")
    public UserResponse updateMe(
        @AuthenticationPrincipal UserPrincipal principal,
        @Valid @RequestBody MeUpdateRequest request,
        HttpServletRequest httpRequest
    ) {
        User user = userService.updateMe(principal.getId(), request);
        operationLogService.log(
            principal.getId(),
            principal.getUsername(),
            "USER_ME_UPDATE",
            "USER",
            String.valueOf(user.getId()),
            "username=" + user.getUsername(),
            RequestUtils.resolveClientIp(httpRequest),
            true
        );
        return UserResponse.from(user);
    }

    @PreAuthorize("hasAuthority('USER_READ')")
    @GetMapping
    public List<UserResponse> list(
        @AuthenticationPrincipal UserPrincipal principal,
        HttpServletRequest httpRequest
    ) {
        List<UserResponse> response = userService.listUsers().stream()
            .map(UserResponse::from)
            .toList();
        operationLogService.log(
            principal.getId(),
            principal.getUsername(),
            "USER_LIST",
            "USER",
            null,
            "list",
            RequestUtils.resolveClientIp(httpRequest),
            true
        );
        return response;
    }

    @PreAuthorize("hasAuthority('USER_WRITE')")
    @PostMapping
    public UserResponse create(
        @AuthenticationPrincipal UserPrincipal principal,
        @Valid @RequestBody UserCreateRequest request,
        HttpServletRequest httpRequest
    ) {
        User user = userService.createUser(request);
        operationLogService.log(
            principal.getId(),
            principal.getUsername(),
            "USER_CREATE",
            "USER",
            String.valueOf(user.getId()),
            "username=" + user.getUsername(),
            RequestUtils.resolveClientIp(httpRequest),
            true
        );
        return UserResponse.from(user);
    }

    @PreAuthorize("hasAuthority('USER_WRITE')")
    @PatchMapping("/{id}")
    public UserResponse update(
        @AuthenticationPrincipal UserPrincipal principal,
        @PathVariable Long id,
        @Valid @RequestBody UserUpdateRequest request,
        HttpServletRequest httpRequest
    ) {
        User user = userService.updateUser(id, request);
        operationLogService.log(
            principal.getId(),
            principal.getUsername(),
            "USER_UPDATE",
            "USER",
            String.valueOf(user.getId()),
            "username=" + user.getUsername(),
            RequestUtils.resolveClientIp(httpRequest),
            true
        );
        return UserResponse.from(user);
    }
}
