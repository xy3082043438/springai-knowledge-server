package com.lamb.springaiknowledgeserver.controller;

import com.lamb.springaiknowledgeserver.auth.UserPrincipal;
import com.lamb.springaiknowledgeserver.dto.DocumentCreateRequest;
import com.lamb.springaiknowledgeserver.dto.DocumentResponse;
import com.lamb.springaiknowledgeserver.dto.DocumentSearchRequest;
import com.lamb.springaiknowledgeserver.entity.Role;
import com.lamb.springaiknowledgeserver.entity.User;
import com.lamb.springaiknowledgeserver.service.DocumentService;
import com.lamb.springaiknowledgeserver.service.UserService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;
    private final UserService userService;

    @PreAuthorize("hasAuthority('DOC_READ')")
    @GetMapping
    public List<DocumentResponse> list(@AuthenticationPrincipal UserPrincipal principal) {
        String roleName = resolveRoleName(principal);
        return documentService.listVisible(roleName).stream()
            .map(DocumentResponse::from)
            .toList();
    }

    @PreAuthorize("hasAuthority('DOC_READ')")
    @GetMapping("/{id}")
    public DocumentResponse get(@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long id) {
        String roleName = resolveRoleName(principal);
        return DocumentResponse.from(documentService.getVisibleById(id, roleName));
    }

    @PreAuthorize("hasAuthority('DOC_READ')")
    @PostMapping("/search")
    public List<DocumentResponse> search(
        @AuthenticationPrincipal UserPrincipal principal,
        @Valid @RequestBody DocumentSearchRequest request
    ) {
        String roleName = resolveRoleName(principal);
        return documentService.searchVisible(roleName, request.getQuery()).stream()
            .map(DocumentResponse::from)
            .toList();
    }

    @PreAuthorize("hasAuthority('DOC_WRITE')")
    @PostMapping
    public DocumentResponse create(@Valid @RequestBody DocumentCreateRequest request) {
        return DocumentResponse.from(documentService.create(request));
    }

    private String resolveRoleName(UserPrincipal principal) {
        User user = userService.getById(principal.getId());
        Role role = user.getRole();
        if (role == null || role.getName() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "User has no role");
        }
        return role.getName();
    }
}
