package com.lamb.springaiknowledgeserver.modules.system.dashboard;

import java.time.Instant;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class DashboardResponse {

    private Instant generatedAt;
    private int trendDays;
    private int keywordDays;
    private int keywordLimit;
    private DashboardOverviewResponse overview;
    private List<DashboardTrendPointResponse> dailyQaTrend;
    private List<DashboardWordCloudItemResponse> hotQuestionKeywords;
    private List<DashboardDistributionItemResponse> documentTypeDistribution;
}


