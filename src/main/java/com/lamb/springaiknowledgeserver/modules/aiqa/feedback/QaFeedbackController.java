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
import java.time.temporal.ChronoUnit;
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
        if (fromInstant == null) {
            fromInstant = Instant.now().minus(30, ChronoUnit.DAYS);
        }
        Instant toInstant = requestInstantParser.parse(to, "to");
        return PageResponse.from(qaFeedbackRepository.search(
            userId,
            fromInstant,
            toInstant,
            PageRequest.of(safePage, safeSize)
        ).map(QaFeedbackResponse::from));
    }

    @PreAuthorize("hasAuthority('FEEDBACK_READ')")
    @GetMapping("/export")
    public org.springframework.http.ResponseEntity<byte[]> export(
        @RequestParam(value = "userId", required = false) Long userId,
        @RequestParam(value = "from", required = false) String from,
        @RequestParam(value = "to", required = false) String to
    ) throws java.io.IOException {
        Instant fromInstant = requestInstantParser.parse(from, "from");
        if (fromInstant == null) {
            fromInstant = Instant.now().minus(30, ChronoUnit.DAYS);
        }
        Instant toInstant = requestInstantParser.parse(to, "to");
        
        java.util.List<QaFeedbackResponse> logs = qaFeedbackRepository.search(
            userId, fromInstant, toInstant, PageRequest.of(0, 10000)
        ).map(QaFeedbackResponse::from).getContent();

        try (org.apache.poi.ss.usermodel.Workbook workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook()) {
            org.apache.poi.ss.usermodel.Sheet sheet = workbook.createSheet("Feedback Logs");
            org.apache.poi.ss.usermodel.Row headerRow = sheet.createRow(0);
            String[] columns = {"反馈ID", "评价人", "关联问答ID", "是否有帮助", "详细评价", "评价时间"};
            for (int i = 0; i < columns.length; i++) {
                headerRow.createCell(i).setCellValue(columns[i]);
            }

            java.time.format.DateTimeFormatter fmt = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(java.time.ZoneId.systemDefault());
            int rowNum = 1;
            for (QaFeedbackResponse log : logs) {
                org.apache.poi.ss.usermodel.Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(log.getId() != null ? log.getId() : 0);
                row.createCell(1).setCellValue(log.getUsername() != null ? log.getUsername() : "");
                row.createCell(2).setCellValue(log.getQaLogId() != null ? log.getQaLogId() : 0);
                row.createCell(3).setCellValue(log.isHelpful() ? "有用" : "无用");
                row.createCell(4).setCellValue(log.getComment() != null ? log.getComment() : "");
                row.createCell(5).setCellValue(log.getCreatedAt() != null ? fmt.format(log.getCreatedAt()) : "");
            }

            java.io.ByteArrayOutputStream outputStream = new java.io.ByteArrayOutputStream();
            workbook.write(outputStream);
            
            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.setContentType(org.springframework.http.MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
            headers.setContentDispositionFormData("attachment", "feedback_logs.xlsx");
            
            return org.springframework.http.ResponseEntity.ok()
                .headers(headers)
                .body(outputStream.toByteArray());
        }
    }
}



