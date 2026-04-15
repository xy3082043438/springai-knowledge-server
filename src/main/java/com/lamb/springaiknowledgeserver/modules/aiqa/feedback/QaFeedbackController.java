package com.lamb.springaiknowledgeserver.modules.aiqa.feedback;

import com.lamb.springaiknowledgeserver.security.auth.UserPrincipal;
import com.lamb.springaiknowledgeserver.core.dto.PageResponse;
import com.lamb.springaiknowledgeserver.modules.aiqa.feedback.QaFeedbackRequest;
import com.lamb.springaiknowledgeserver.modules.aiqa.feedback.QaFeedbackResponse;
import com.lamb.springaiknowledgeserver.modules.aiqa.feedback.QaFeedback;
import com.lamb.springaiknowledgeserver.modules.aiqa.chat.QaLog;
import com.lamb.springaiknowledgeserver.modules.aiqa.feedback.QaFeedbackRepository;
import com.lamb.springaiknowledgeserver.modules.aiqa.chat.QaLogRepository;
import com.lamb.springaiknowledgeserver.core.util.RequestInstantParser;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/feedback")
@RequiredArgsConstructor
public class QaFeedbackController {

    private final QaFeedbackRepository qaFeedbackRepository;
    private final QaLogRepository qaLogRepository;
    private final RequestInstantParser requestInstantParser;

    @PreAuthorize("hasAuthority('FEEDBACK_WRITE')")
    @PostMapping
    public QaFeedbackResponse create(
        @AuthenticationPrincipal UserPrincipal principal,
        @Valid @RequestBody QaFeedbackRequest request
    ) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "未登录");
        }
        QaLog qaLog = qaLogRepository.findById(request.getQaLogId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "问答记录不存在"));
        if (qaLog.getUserId() != null && !Objects.equals(qaLog.getUserId(), principal.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权限反馈该记录");
        }
        QaFeedback feedback = qaFeedbackRepository.findByQaLogIdAndUserId(qaLog.getId(), principal.getId())
            .orElseGet(QaFeedback::new);
        feedback.setQaLogId(qaLog.getId());
        feedback.setUserId(principal.getId());
        feedback.setUsername(principal.getUsername());
        feedback.setHelpful(Boolean.TRUE.equals(request.getHelpful()));
        feedback.setComment(request.getComment());
        QaFeedback saved = qaFeedbackRepository.save(feedback);
        return QaFeedbackResponse.from(saved);
    }

    @PreAuthorize("hasAuthority('FEEDBACK_READ')")
    @GetMapping
    public PageResponse<QaFeedbackResponse> search(
        @RequestParam(value = "userId", required = false) Long userId,
        @RequestParam(value = "from", required = false) String from,
        @RequestParam(value = "to", required = false) String to,
        @RequestParam(value = "page", defaultValue = "0") int page,
        @RequestParam(value = "size", defaultValue = "50") int size
    ) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(200, Math.max(1, size));
        Instant fromInstant = requestInstantParser.parse(from, "from");
        Instant toInstant = requestInstantParser.parse(to, "to");
        return PageResponse.from(qaFeedbackRepository.search(
            userId,
            fromInstant,
            toInstant,
            PageRequest.of(safePage, safeSize)
        ).map(QaFeedbackResponse::from));
    }
}



