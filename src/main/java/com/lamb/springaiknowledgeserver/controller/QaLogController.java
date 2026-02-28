package com.lamb.springaiknowledgeserver.controller;

import com.lamb.springaiknowledgeserver.dto.QaLogResponse;
import com.lamb.springaiknowledgeserver.repository.QaLogRepository;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
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

    @PreAuthorize("hasAuthority('LOG_READ')")
    @GetMapping
    public List<QaLogResponse> search(
        @RequestParam(value = "userId", required = false) Long userId,
        @RequestParam(value = "from", required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
        @RequestParam(value = "to", required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to
    ) {
        return qaLogRepository.search(userId, from, to).stream()
            .map(QaLogResponse::from)
            .toList();
    }
}
