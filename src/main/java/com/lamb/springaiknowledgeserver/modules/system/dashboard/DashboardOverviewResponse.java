package com.lamb.springaiknowledgeserver.modules.system.dashboard;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class DashboardOverviewResponse {

    private long totalUsers;
    private long totalRoles;
    private long totalDocuments;
    private long totalQaCount;
    private long todayQaCount;
}


