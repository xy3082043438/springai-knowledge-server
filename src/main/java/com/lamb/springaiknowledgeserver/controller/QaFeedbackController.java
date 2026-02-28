package com.lamb.springaiknowledgeserver.controller;

import com.lamb.springaiknowledgeserver.auth.UserPrincipal;
import com.lamb.springaiknowledgeserver.dto.QaFeedbackRequest;
import com.lamb.springaiknowledgeserver.dto.QaFeedbackResponse;
import com.lamb.springaiknowledgeserver.entity.QaFeedback;
import com.lamb.springaiknowledgeserver.entity.QaLog;
import com.lamb.springaiknowledgeserver.repository.QaFeedbackRepository;
import com.lamb.springaiknowledgeserver.repository.QaLogRepository;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
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

    @PreAuthorize("hasAuthority('FEEDBACK_WRITE')")
    @PostMapping
    public QaFeedbackResponse create(
        @AuthenticationPrincipal UserPrincipal principal,
        @Valid @RequestBody QaFeedbackRequest request
    ) {
        QaLog qaLog = qaLogRepository.findById(request.getQaLogId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "问答记录不存在"));
        if (qaLog.getUserId() != null
            && principal != null
            && !Objects.equals(qaLog.getUserId(), principal.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权限反馈该记录");
        }

        QaFeedback feedback = new QaFeedback();
        feedback.setQaLogId(qaLog.getId());
        if (principal != null) {
            feedback.setUserId(principal.getId());
            feedback.setUsername(principal.getUsername());
        }
        feedback.setHelpful(Boolean.TRUE.equals(request.getHelpful()));
        feedback.setComment(request.getComment());
        QaFeedback saved = qaFeedbackRepository.save(feedback);
        return QaFeedbackResponse.from(saved);
    }

    @PreAuthorize("hasAuthority('FEEDBACK_READ')")
    @GetMapping
    public List<QaFeedbackResponse> search(
        @RequestParam(value = "userId", required = false) Long userId,
        @RequestParam(value = "from", required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
        @RequestParam(value = "to", required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to
    ) {
        return qaFeedbackRepository.search(userId, from, to).stream()
            .map(QaFeedbackResponse::from)
            .toList();
    }
}
