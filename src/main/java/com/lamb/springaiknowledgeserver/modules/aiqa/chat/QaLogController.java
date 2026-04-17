package com.lamb.springaiknowledgeserver.modules.aiqa.chat;

import com.lamb.springaiknowledgeserver.core.dto.PageResponse;
import com.lamb.springaiknowledgeserver.core.util.RequestInstantParser;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/logs/qa")
@RequiredArgsConstructor
public class QaLogController {

    private final QaLogRepository qaLogRepository;
    private final RequestInstantParser requestInstantParser;

    @PreAuthorize("hasAuthority('LOG_READ')")
    @GetMapping
    public PageResponse<QaLogResponse> search(
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
        return PageResponse.from(qaLogRepository.search(
            userId,
            fromInstant,
            toInstant,
            PageRequest.of(safePage, safeSize)
        ).map(QaLogResponse::from));
    }

    @PreAuthorize("hasAuthority('LOG_EXPORT')")
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
        
        java.util.List<QaLogResponse> logs = qaLogRepository.search(
            userId, fromInstant, toInstant, PageRequest.of(0, 10000)
        ).map(QaLogResponse::from).getContent();

        try (org.apache.poi.ss.usermodel.Workbook workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook()) {
            org.apache.poi.ss.usermodel.Sheet sheet = workbook.createSheet("QA Logs");
            org.apache.poi.ss.usermodel.Row headerRow = sheet.createRow(0);
            String[] columns = {"记录ID", "用户名", "提问", "系统回答", "检索上下文JSON", "分配角色", "TopK配置", "提问时间"};
            for (int i = 0; i < columns.length; i++) {
                headerRow.createCell(i).setCellValue(columns[i]);
            }

            java.time.format.DateTimeFormatter fmt = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(java.time.ZoneId.systemDefault());
            int rowNum = 1;
            for (QaLogResponse log : logs) {
                org.apache.poi.ss.usermodel.Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(log.getId() != null ? log.getId() : 0);
                row.createCell(1).setCellValue(log.getUsername() != null ? log.getUsername() : "");
                row.createCell(2).setCellValue(log.getQuestion() != null ? log.getQuestion() : "");
                row.createCell(3).setCellValue(log.getAnswer() != null ? log.getAnswer() : "");
                row.createCell(4).setCellValue(log.getRetrievalJson() != null ? log.getRetrievalJson() : "");
                row.createCell(5).setCellValue(log.getRoleName() != null ? log.getRoleName() : "");
                row.createCell(6).setCellValue(log.getTopK() != null ? log.getTopK() : 0);
                row.createCell(7).setCellValue(log.getCreatedAt() != null ? fmt.format(log.getCreatedAt()) : "");
            }

            java.io.ByteArrayOutputStream outputStream = new java.io.ByteArrayOutputStream();
            workbook.write(outputStream);
            
            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.setContentType(org.springframework.http.MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
            headers.setContentDispositionFormData("attachment", "qa_logs.xlsx");
            
            return org.springframework.http.ResponseEntity.ok()
                .headers(headers)
                .body(outputStream.toByteArray());
        }
    }
}



