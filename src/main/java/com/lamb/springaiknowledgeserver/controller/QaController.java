package com.lamb.springaiknowledgeserver.controller;

import com.lamb.springaiknowledgeserver.auth.UserPrincipal;
import com.lamb.springaiknowledgeserver.dto.QaRequest;
import com.lamb.springaiknowledgeserver.dto.QaResponse;
import com.lamb.springaiknowledgeserver.entity.Role;
import com.lamb.springaiknowledgeserver.entity.User;
import com.lamb.springaiknowledgeserver.service.QaService;
import com.lamb.springaiknowledgeserver.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/qa")
@RequiredArgsConstructor
public class QaController {

    private final QaService qaService;
    private final UserService userService;

    @PreAuthorize("hasAuthority('DOC_READ')")
    @PostMapping
    public QaResponse ask(
        @AuthenticationPrincipal UserPrincipal principal,
        @Valid @RequestBody QaRequest request
    ) {
        User user = userService.getById(principal.getId());
        Role role = user.getRole();
        if (role == null || role.getName() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "User has no role");
        }
        return qaService.answer(role.getName(), request.getQuestion());
    }
}
