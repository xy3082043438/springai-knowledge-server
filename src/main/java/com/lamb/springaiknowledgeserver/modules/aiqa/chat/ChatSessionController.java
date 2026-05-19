package com.lamb.springaiknowledgeserver.modules.aiqa.chat;

import com.lamb.springaiknowledgeserver.security.auth.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.util.Objects;

@RestController
@RequestMapping("/api/aiqa/sessions")
@RequiredArgsConstructor
public class ChatSessionController {

    private final ChatSessionRepository chatSessionRepository;
    private final QaLogRepository qaLogRepository;

    @PreAuthorize("hasAuthority('DOC_READ')")
    @GetMapping
    public List<ChatSession> list(@AuthenticationPrincipal UserPrincipal principal) {
        Long userId = principal.getId();
        
        // Migrate orphans first
        List<QaLog> orphanLogs = qaLogRepository.findOrphansByUserId(userId);
        if (!orphanLogs.isEmpty()) {
            ChatSession session = new ChatSession();
            session.setUserId(userId);
            session.setTitle("历史对话");
            session.setLatestQuestion(orphanLogs.get(orphanLogs.size() - 1).getQuestion());
            session = chatSessionRepository.save(session);
            
            qaLogRepository.migrateOrphans(userId, session.getId());
        }
        
        return chatSessionRepository.findByUserIdOrderByUpdatedAtDesc(userId);
    }

    @PreAuthorize("hasAuthority('DOC_READ')")
    @GetMapping("/{id}/logs")
    public List<QaLog> listLogs(@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long id) {
        assertOwnership(principal.getId(), id);
        return qaLogRepository.findBySessionIdOrderByCreatedAtAsc(id);
    }

    @PreAuthorize("hasAuthority('DOC_READ')")
    @DeleteMapping("/{id}")
    public void delete(@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long id) {
        assertOwnership(principal.getId(), id);
        chatSessionRepository.deleteById(id);
    }

    private void assertOwnership(Long userId, Long sessionId) {
        ChatSession session = chatSessionRepository.findById(sessionId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "会话不存在"));
        if (!Objects.equals(session.getUserId(), userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权访问该会话");
        }
    }
}
