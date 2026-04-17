package com.lamb.springaiknowledgeserver.modules.aiqa.chat;

import com.lamb.springaiknowledgeserver.security.auth.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;

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
    public List<QaLog> listLogs(@PathVariable Long id) {
        return qaLogRepository.findBySessionIdOrderByCreatedAtAsc(id);
    }

    @PreAuthorize("hasAuthority('DOC_READ')")
    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        chatSessionRepository.deleteById(id);
        // Note: We could delete logs too, but keeping them might be better for auditing
    }
}
