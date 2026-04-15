package com.lamb.springaiknowledgeserver.modules.aiqa.chat;

import com.lamb.springaiknowledgeserver.core.dto.PageResponse;
import com.lamb.springaiknowledgeserver.modules.aiqa.chat.QaLogResponse;
import com.lamb.springaiknowledgeserver.modules.aiqa.chat.QaLogRepository;
import com.lamb.springaiknowledgeserver.core.util.RequestInstantParser;
import java.time.Instant;
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
        Instant toInstant = requestInstantParser.parse(to, "to");
        return PageResponse.from(qaLogRepository.search(
            userId,
            fromInstant,
            toInstant,
            PageRequest.of(safePage, safeSize)
        ).map(QaLogResponse::from));
    }
}



