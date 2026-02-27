package com.lamb.springaiknowledgeserver.service;

import com.lamb.springaiknowledgeserver.dto.DocumentCreateRequest;
import com.lamb.springaiknowledgeserver.entity.Document;
import com.lamb.springaiknowledgeserver.entity.Role;
import com.lamb.springaiknowledgeserver.repository.DocumentRepository;
import com.lamb.springaiknowledgeserver.repository.RoleRepository;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final RoleRepository roleRepository;

    public Document create(DocumentCreateRequest request) {
        Set<Role> roles = resolveRoles(request.getAllowedRoles());
        Document document = new Document();
        document.setTitle(request.getTitle());
        document.setContent(request.getContent());
        document.setAllowedRoles(roles);
        return documentRepository.save(document);
    }

    public List<Document> listVisible(String roleName) {
        return documentRepository.findVisibleByRoles(Set.of(roleName));
    }

    public List<Document> searchVisible(String roleName, String query) {
        return documentRepository.searchVisibleByRoles(Set.of(roleName), query);
    }

    public Document getVisibleById(Long id, String roleName) {
        Document document = documentRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found"));
        if (!hasRoleAccess(document, roleName)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No access to document");
        }
        return document;
    }

    private Set<Role> resolveRoles(Collection<String> roleNames) {
        if (roleNames == null || roleNames.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Allowed roles required");
        }
        Set<String> normalized = new HashSet<>();
        for (String name : roleNames) {
            if (name != null && !name.isBlank()) {
                normalized.add(name);
            }
        }
        if (normalized.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Allowed roles required");
        }
        Collection<Role> roles = roleRepository.findByNameIn(normalized);
        if (roles.size() != normalized.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Role not found");
        }
        return new HashSet<>(roles);
    }

    private boolean hasRoleAccess(Document document, String roleName) {
        for (Role role : document.getAllowedRoles()) {
            if (role.getName().equals(roleName)) {
                return true;
            }
        }
        return false;
    }
}
