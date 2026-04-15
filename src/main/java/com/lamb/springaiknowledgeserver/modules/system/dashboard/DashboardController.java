package com.lamb.springaiknowledgeserver.modules.system.dashboard;

import com.lamb.springaiknowledgeserver.security.auth.UserPrincipal;
import com.lamb.springaiknowledgeserver.modules.system.dashboard.DashboardResponse;
import com.lamb.springaiknowledgeserver.modules.system.dashboard.DashboardService;
import com.lamb.springaiknowledgeserver.modules.system.log.OperationLogService;
import com.lamb.springaiknowledgeserver.core.util.RequestUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;
    private final OperationLogService operationLogService;

    @PreAuthorize("hasAuthority('LOG_READ')")
    @GetMapping
    public DashboardResponse getDashboard(
        @AuthenticationPrincipal UserPrincipal principal,
        @RequestParam(value = "trendDays", defaultValue = "14") int trendDays,
        @RequestParam(value = "keywordDays", defaultValue = "30") int keywordDays,
        @RequestParam(value = "keywordLimit", defaultValue = "20") int keywordLimit,
        HttpServletRequest httpRequest
    ) {
        DashboardResponse response = dashboardService.getDashboard(trendDays, keywordDays, keywordLimit);
        operationLogService.log(
            principal.getId(),
            principal.getUsername(),
            "DASHBOARD_VIEW",
            "DASHBOARD",
            null,
            "trendDays=" + response.getTrendDays()
                + ",keywordDays=" + response.getKeywordDays()
                + ",keywordLimit=" + response.getKeywordLimit(),
            RequestUtils.resolveClientIp(httpRequest),
            true
        );
        return response;
    }
}



