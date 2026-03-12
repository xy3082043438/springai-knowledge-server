package com.lamb.springaiknowledgeserver.controller;

import com.lamb.springaiknowledgeserver.dto.OperationLogResponse;
import com.lamb.springaiknowledgeserver.dto.PageResponse;
import com.lamb.springaiknowledgeserver.repository.OperationLogRepository;
import com.lamb.springaiknowledgeserver.util.RequestInstantParser;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/logs/operations")
@RequiredArgsConstructor
public class OperationLogController {

    private final OperationLogRepository operationLogRepository;
    private final RequestInstantParser requestInstantParser;

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
        Instant toInstant = requestInstantParser.parse(to, "to");
        return PageResponse.from(operationLogRepository.search(
            userId,
            fromInstant,
            toInstant,
            PageRequest.of(safePage, safeSize)
        ).map(OperationLogResponse::from));
    }
}
