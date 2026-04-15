package com.lamb.springaiknowledgeserver.modules.system.dashboard;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class DashboardDistributionItemResponse {

    private String type;
    private String label;
    private long value;
}


