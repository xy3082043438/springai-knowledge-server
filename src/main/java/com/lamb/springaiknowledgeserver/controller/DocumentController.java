package com.lamb.springaiknowledgeserver.controller;

import com.lamb.springaiknowledgeserver.auth.UserPrincipal;
import com.lamb.springaiknowledgeserver.dto.DocumentChunkPreviewResponse;
import com.lamb.springaiknowledgeserver.dto.DocumentCreateRequest;
import com.lamb.springaiknowledgeserver.dto.DocumentResponse;
import com.lamb.springaiknowledgeserver.dto.DocumentSearchRequest;
import com.lamb.springaiknowledgeserver.dto.DocumentSummaryResponse;
import com.lamb.springaiknowledgeserver.dto.DocumentUpdateRequest;
import com.lamb.springaiknowledgeserver.entity.Role;
import com.lamb.springaiknowledgeserver.entity.User;
import com.lamb.springaiknowledgeserver.service.DocumentService;
import com.lamb.springaiknowledgeserver.service.UserService;
import jakarta.validation.Valid;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;
    private final UserService userService;

    @PreAuthorize("hasAuthority('DOC_READ')")
    @GetMapping
    public List<DocumentSummaryResponse> list(@AuthenticationPrincipal UserPrincipal principal) {
        String roleName = resolveRoleName(principal);
        return documentService.listVisible(roleName).stream()
            .map(DocumentSummaryResponse::from)
            .toList();
    }

    @PreAuthorize("hasAuthority('DOC_READ')")
    @GetMapping("/{id}")
    public DocumentResponse get(@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long id) {
        String roleName = resolveRoleName(principal);
        return DocumentResponse.from(documentService.getVisibleById(id, roleName));
    }

    @PreAuthorize("hasAuthority('DOC_READ')")
    @GetMapping("/chunks/{chunkId}")
    public DocumentChunkPreviewResponse previewChunk(
        @AuthenticationPrincipal UserPrincipal principal,
        @PathVariable Long chunkId
    ) {
        String roleName = resolveRoleName(principal);
        return documentService.getChunkPreview(chunkId, roleName);
    }

    @PreAuthorize("hasAuthority('DOC_READ')")
    @PostMapping("/search")
    public List<DocumentSummaryResponse> search(
        @AuthenticationPrincipal UserPrincipal principal,
        @Valid @RequestBody DocumentSearchRequest request
    ) {
        String roleName = resolveRoleName(principal);
        return documentService.searchVisible(roleName, request.getQuery()).stream()
            .map(DocumentSummaryResponse::from)
            .toList();
    }

    @PreAuthorize("hasAuthority('DOC_WRITE')")
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public DocumentSummaryResponse upload(
        @RequestParam("file") MultipartFile file,
        @RequestParam("allowedRoles") List<String> allowedRoles,
        @RequestParam(value = "title", required = false) String title
    ) {
        List<String> normalized = normalizeRoles(allowedRoles);
        return DocumentSummaryResponse.from(documentService.upload(file, title, normalized));
    }

    @PreAuthorize("hasAuthority('DOC_WRITE')")
    @PostMapping
    public DocumentResponse create(@Valid @RequestBody DocumentCreateRequest request) {
        return DocumentResponse.from(documentService.createText(request));
    }

    @PreAuthorize("hasAuthority('DOC_WRITE')")
    @PatchMapping("/{id}")
    public DocumentResponse update(@PathVariable Long id, @Valid @RequestBody DocumentUpdateRequest request) {
        return DocumentResponse.from(documentService.update(id, request));
    }

    @PreAuthorize("hasAuthority('DOC_WRITE')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        documentService.delete(id);
        return ResponseEntity.noContent().build();
    }

    private String resolveRoleName(UserPrincipal principal) {
        User user = userService.getById(principal.getId());
        Role role = user.getRole();
        if (role == null || role.getName() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "User has no role");
        }
        return role.getName();
    }

    private List<String> normalizeRoles(List<String> roles) {
        if (roles == null || roles.isEmpty()) {
            return List.of();
        }
        if (roles.size() == 1 && roles.getFirst() != null && roles.getFirst().contains(",")) {
            String[] parts = roles.getFirst().split(",");
            List<String> result = new ArrayList<>();
            for (String part : parts) {
                String trimmed = part.trim();
                if (!trimmed.isEmpty()) {
                    result.add(trimmed);
                }
            }
            return result;
        }
        return roles;
    }
}
