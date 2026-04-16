package com.lamb.springaiknowledgeserver.modules.knowledge.document;

import com.lamb.springaiknowledgeserver.security.auth.UserPrincipal;
import com.lamb.springaiknowledgeserver.modules.knowledge.document.DocumentChunkPreviewResponse;
import com.lamb.springaiknowledgeserver.modules.knowledge.document.DocumentCreateRequest;
import com.lamb.springaiknowledgeserver.modules.knowledge.document.DocumentReindexResponse;
import com.lamb.springaiknowledgeserver.modules.knowledge.document.DocumentResponse;
import com.lamb.springaiknowledgeserver.modules.knowledge.document.DocumentSearchRequest;
import com.lamb.springaiknowledgeserver.modules.knowledge.document.DocumentSummaryResponse;
import com.lamb.springaiknowledgeserver.modules.knowledge.document.DocumentUpdateRequest;
import com.lamb.springaiknowledgeserver.modules.knowledge.document.Document;
import com.lamb.springaiknowledgeserver.modules.system.role.Role;
import com.lamb.springaiknowledgeserver.modules.system.user.User;
import com.lamb.springaiknowledgeserver.modules.knowledge.document.DocumentService;
import com.lamb.springaiknowledgeserver.modules.system.log.OperationLogService;
import com.lamb.springaiknowledgeserver.modules.system.user.UserService;
import com.lamb.springaiknowledgeserver.core.util.RequestUtils;
import jakarta.servlet.http.HttpServletRequest;
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
    private final DocumentRepository documentRepository;
    private final UserService userService;
    private final OperationLogService operationLogService;

    @PreAuthorize("hasAuthority('DOC_READ')")
    @GetMapping
    public List<DocumentSummaryResponse> list(
        @AuthenticationPrincipal UserPrincipal principal,
        HttpServletRequest httpRequest
    ) {
        String roleName = resolveRoleName(principal);
        List<DocumentSummaryResponse> response = documentService.listVisible(roleName).stream()
            .map(DocumentSummaryResponse::from)
            .toList();
        operationLogService.log(
            principal.getId(),
            principal.getUsername(),
            "DOC_LIST",
            "DOCUMENT",
            null,
            "role=" + roleName,
            RequestUtils.resolveClientIp(httpRequest),
            true
        );
        return response;
    }

    @PreAuthorize("hasAuthority('DOC_READ')")
    @GetMapping("/{id}")
    public DocumentResponse get(
        @AuthenticationPrincipal UserPrincipal principal,
        @PathVariable Long id,
        HttpServletRequest httpRequest
    ) {
        String roleName = resolveRoleName(principal);
        Document document = documentService.getVisibleById(id, roleName);
        operationLogService.log(
            principal.getId(),
            principal.getUsername(),
            "DOC_VIEW",
            "DOCUMENT",
            String.valueOf(document.getId()),
            "title=" + document.getTitle(),
            RequestUtils.resolveClientIp(httpRequest),
            true
        );
        return DocumentResponse.from(document);
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
        @Valid @RequestBody DocumentSearchRequest request,
        HttpServletRequest httpRequest
    ) {
        String roleName = resolveRoleName(principal);
        List<DocumentSummaryResponse> response = documentService.searchVisible(roleName, request.getQuery()).stream()
            .map(DocumentSummaryResponse::from)
            .toList();
        operationLogService.log(
            principal.getId(),
            principal.getUsername(),
            "DOC_SEARCH",
            "DOCUMENT",
            null,
            "query=" + request.getQuery(),
            RequestUtils.resolveClientIp(httpRequest),
            true
        );
        return response;
    }

    @PreAuthorize("hasAuthority('DOC_WRITE')")
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public DocumentSummaryResponse upload(
        @AuthenticationPrincipal UserPrincipal principal,
        @RequestParam("file") MultipartFile file,
        @RequestParam("allowedRoles") List<String> allowedRoles,
        @RequestParam(value = "title", required = false) String title,
        HttpServletRequest httpRequest
    ) {
        List<String> normalized = normalizeRoles(allowedRoles);
        Document document = documentService.upload(file, title, normalized);
        operationLogService.log(
            principal.getId(),
            principal.getUsername(),
            "DOC_UPLOAD",
            "DOCUMENT",
            String.valueOf(document.getId()),
            "title=" + document.getTitle(),
            RequestUtils.resolveClientIp(httpRequest),
            true
        );
        return DocumentSummaryResponse.from(document);
    }

    @PreAuthorize("hasAuthority('DOC_WRITE')")
    @PostMapping(value = "/{id}/file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public DocumentSummaryResponse replaceFile(
        @AuthenticationPrincipal UserPrincipal principal,
        @PathVariable Long id,
        @RequestParam("file") MultipartFile file,
        @RequestParam(value = "allowedRoles", required = false) List<String> allowedRoles,
        @RequestParam(value = "title", required = false) String title,
        HttpServletRequest httpRequest
    ) {
        List<String> normalized = allowedRoles == null ? null : normalizeRoles(allowedRoles);
        if (normalized != null && normalized.isEmpty()) {
            normalized = null;
        }
        Document document = documentService.replaceFile(id, file, title, normalized);
        operationLogService.log(
            principal.getId(),
            principal.getUsername(),
            "DOC_REPLACE_FILE",
            "DOCUMENT",
            String.valueOf(document.getId()),
            "title=" + document.getTitle(),
            RequestUtils.resolveClientIp(httpRequest),
            true
        );
        return DocumentSummaryResponse.from(document);
    }

    @PreAuthorize("hasAuthority('DOC_WRITE')")
    @PostMapping
    public DocumentResponse create(
        @AuthenticationPrincipal UserPrincipal principal,
        @Valid @RequestBody DocumentCreateRequest request,
        HttpServletRequest httpRequest
    ) {
        Document document = documentService.createText(request);
        operationLogService.log(
            principal.getId(),
            principal.getUsername(),
            "DOC_CREATE",
            "DOCUMENT",
            String.valueOf(document.getId()),
            "title=" + document.getTitle(),
            RequestUtils.resolveClientIp(httpRequest),
            true
        );
        return DocumentResponse.from(document);
    }

    @PreAuthorize("hasAuthority('DOC_WRITE')")
    @PatchMapping("/{id}")
    public DocumentResponse update(
        @AuthenticationPrincipal UserPrincipal principal,
        @PathVariable Long id,
        @Valid @RequestBody DocumentUpdateRequest request,
        HttpServletRequest httpRequest
    ) {
        Document document = documentService.update(id, request);
        operationLogService.log(
            principal.getId(),
            principal.getUsername(),
            "DOC_UPDATE",
            "DOCUMENT",
            String.valueOf(document.getId()),
            "title=" + document.getTitle(),
            RequestUtils.resolveClientIp(httpRequest),
            true
        );
        return DocumentResponse.from(document);
    }

    @PreAuthorize("hasAuthority('DOC_WRITE')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
        @AuthenticationPrincipal UserPrincipal principal,
        @PathVariable Long id,
        HttpServletRequest httpRequest
    ) {
        documentService.delete(id);
        operationLogService.log(
            principal.getId(),
            principal.getUsername(),
            "DOC_DELETE",
            "DOCUMENT",
            String.valueOf(id),
            "delete",
            RequestUtils.resolveClientIp(httpRequest),
            true
        );
        return ResponseEntity.noContent().build();
    }

    @PreAuthorize("hasAuthority('DOC_WRITE')")
    @PostMapping("/{id}/reindex")
    public DocumentReindexResponse reindexOne(
        @AuthenticationPrincipal UserPrincipal principal,
        @PathVariable Long id,
        HttpServletRequest httpRequest
    ) {
        DocumentReindexResponse response = documentService.reindexOne(id);
        operationLogService.log(
            principal.getId(),
            principal.getUsername(),
            "DOC_REINDEX",
            "DOCUMENT",
            String.valueOf(id),
            "reindex",
            RequestUtils.resolveClientIp(httpRequest),
            true
        );
        return response;
    }

    @PreAuthorize("hasAuthority('DOC_WRITE')")
    @PostMapping("/reindex")
    public DocumentReindexResponse reindexAll(
        @AuthenticationPrincipal UserPrincipal principal,
        HttpServletRequest httpRequest
    ) {
        DocumentReindexResponse response = documentService.reindexAll();
        operationLogService.log(
            principal.getId(),
            principal.getUsername(),
            "DOC_REINDEX_ALL",
            "DOCUMENT",
            null,
            "reindexAll",
            RequestUtils.resolveClientIp(httpRequest),
            true
        );
        return response;
    }

    @PreAuthorize("hasAuthority('DOC_READ')")
    @GetMapping("/{id}/file")
    public ResponseEntity<org.springframework.core.io.Resource> getFile(
        @AuthenticationPrincipal UserPrincipal principal,
        @PathVariable Long id
    ) {
        String roleName = resolveRoleName(principal);
        Document document = documentRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "文档不存在"));

        System.out.println("DEBUG: Requesting file preview for ID=" + id);
        System.out.println("DEBUG: Document Storage Path in DB=" + document.getStoragePath());
        
        org.springframework.core.io.Resource resource = documentService.getFileAsResource(id, roleName);
        
        String encodedFileName = java.net.URLEncoder.encode(document.getFileName(), java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20");
        String contentDisposition = "inline; filename*=UTF-8''" + encodedFileName;
        
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(document.getContentType() != null ? document.getContentType() : "application/octet-stream"))
            .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, contentDisposition)
            .body(resource);
    }

    private String resolveRoleName(UserPrincipal principal) {
        User user = userService.getById(principal.getId());
        Role role = user.getRole();
        if (role == null || role.getName() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "用户未分配角色");
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



