package com.lamb.springaiknowledgeserver.modules.system.log;

import com.lamb.springaiknowledgeserver.modules.system.log.OperationLogResponse;
import com.lamb.springaiknowledgeserver.core.dto.PageResponse;
import com.lamb.springaiknowledgeserver.modules.system.log.OperationLogRepository;
import com.lamb.springaiknowledgeserver.core.util.RequestInstantParser;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

@RestController
@RequestMapping("/api/logs/operations")
@RequiredArgsConstructor
public class OperationLogController {

    private final OperationLogRepository operationLogRepository;
    private final RequestInstantParser requestInstantParser;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    @PreAuthorize("hasAuthority('LOG_READ')")
    @GetMapping
    public PageResponse<OperationLogResponse> search(
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
        return PageResponse.from(operationLogRepository.search(
            userId,
            fromInstant,
            toInstant,
            PageRequest.of(safePage, safeSize)
        ).map(OperationLogResponse::from));
    }

    @PreAuthorize("hasAuthority('LOG_READ')")
    @GetMapping("/export")
    public ResponseEntity<byte[]> export(
        @RequestParam(value = "userId", required = false) Long userId,
        @RequestParam(value = "from", required = false) String from,
        @RequestParam(value = "to", required = false) String to
    ) throws IOException {
        Instant fromInstant = requestInstantParser.parse(from, "from");
        if (fromInstant == null) {
            fromInstant = Instant.now().minus(30, ChronoUnit.DAYS);
        }
        Instant toInstant = requestInstantParser.parse(to, "to");
        
        List<OperationLogResponse> logs = operationLogRepository.search(
            userId,
            fromInstant,
            toInstant,
            PageRequest.of(0, 10000)
        ).map(OperationLogResponse::from).getContent();

        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Operation Logs");
            Row headerRow = sheet.createRow(0);
            String[] columns = {"记录ID", "用户ID", "用户名", "操作动作", "目标资源", "资源ID", "日志详请", "请求IP", "是否成功", "操作时间"};
            for (int i = 0; i < columns.length; i++) {
                headerRow.createCell(i).setCellValue(columns[i]);
            }

            int rowNum = 1;
            for (OperationLogResponse log : logs) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(log.getId() != null ? log.getId() : 0);
                row.createCell(1).setCellValue(log.getUserId() != null ? log.getUserId() : 0);
                row.createCell(2).setCellValue(log.getUsername() != null ? log.getUsername() : "");
                row.createCell(3).setCellValue(log.getAction() != null ? log.getAction() : "");
                row.createCell(4).setCellValue(log.getResource() != null ? log.getResource() : "");
                row.createCell(5).setCellValue(log.getResourceId() != null ? log.getResourceId() : "");
                row.createCell(6).setCellValue(log.getDetail() != null ? log.getDetail() : "");
                row.createCell(7).setCellValue(log.getIp() != null ? log.getIp() : "");
                row.createCell(8).setCellValue(log.isSuccess() ? "成功" : "失败");
                row.createCell(9).setCellValue(log.getCreatedAt() != null ? DATE_FORMATTER.format(log.getCreatedAt()) : "");
            }

            for (int i = 0; i < columns.length; i++) {
                sheet.autoSizeColumn(i);
            }

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            workbook.write(outputStream);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
            headers.setContentDispositionFormData("attachment", "operation_logs.xlsx");
            
            return ResponseEntity.ok()
                .headers(headers)
                .body(outputStream.toByteArray());
        }
    }
}



