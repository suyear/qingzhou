package com.qingzhou.modules.dashboard.dto;

import lombok.Data;

@Data
public class TrendPointVO {

    /** yyyy-MM-dd */
    private String date;
    private long total;
    private long successCount;
    private long failedCount;
    private long runningCount;
}
