package com.lamb.springaiknowledgeserver.modules.aiqa.chat;

import com.lamb.springaiknowledgeserver.security.auth.UserPrincipal;
import com.lamb.springaiknowledgeserver.modules.aiqa.chat.QaRequest;
import com.lamb.springaiknowledgeserver.modules.system.role.Role;
import com.lamb.springaiknowledgeserver.modules.system.user.User;
import com.lamb.springaiknowledgeserver.modules.aiqa.chat.QaService;
import com.lamb.springaiknowledgeserver.modules.system.user.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import reactor.core.publisher.Flux;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;

@RestController
@RequestMapping("/api/qa")
@RequiredArgsConstructor
public class QaController {

    private final QaService qaService;
    private final UserService userService;

    @PreAuthorize("hasAuthority('QA_READ')")
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<QaResponse> streamAsk(
        @AuthenticationPrincipal UserPrincipal principal,
        @Valid @RequestBody QaRequest request
    ) {
        User user = userService.getById(principal.getId());
        Role role = user.getRole();
        if (role == null || role.getName() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "用户未分配角色");
        }
        return qaService.streamAnswer(principal.getId(), principal.getUsername(), role.getName(), request.getQuestion(), request.getSessionId());
    }

    @PreAuthorize("hasAuthority('QA_READ')")
    @GetMapping("/suggestions")
    public List<String> getSuggestions(
        @AuthenticationPrincipal UserPrincipal principal
    ) {
        User user = userService.getById(principal.getId());
        Role role = user.getRole();
        if (role == null) return List.of();
        return qaService.getSuggestions(role.getName());
    }

}



