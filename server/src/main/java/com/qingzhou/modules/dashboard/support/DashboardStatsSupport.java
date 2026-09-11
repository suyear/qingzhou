package com.qingzhou.modules.dashboard.support;

import com.qingzhou.modules.dashboard.dto.NamedCountVO;
import com.qingzhou.modules.dashboard.dto.TrendPointVO;
import com.qingzhou.modules.dashboard.dto.TrendRow;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class DashboardStatsSupport {

    public static final int MIN_DAYS = 1;
    public static final int MAX_DAYS = 30;
    public static final int DEFAULT_DAYS = 7;

    private DashboardStatsSupport() {
    }

    public static int clampDays(Integer days) {
        if (days == null) {
            return DEFAULT_DAYS;
        }
        return Math.min(MAX_DAYS, Math.max(MIN_DAYS, days));
    }

    public static LocalDateTime rangeStart(int days, LocalDate today) {
        return today.minusDays(days - 1L).atStartOfDay();
    }

    public static List<TrendPointVO> fillTrend(List<TrendRow> rows, int days, LocalDate today) {
        Map<String, TrendRow> byDay = new LinkedHashMap<>();
        if (rows != null) {
            for (TrendRow row : rows) {
                if (row != null && row.getBucket() != null) {
                    byDay.put(row.getBucket(), row);
                }
            }
        }
        List<TrendPointVO> points = new ArrayList<>(days);
        LocalDate start = today.minusDays(days - 1L);
        for (int i = 0; i < days; i++) {
            LocalDate day = start.plusDays(i);
            String key = day.toString();
            TrendRow row = byDay.get(key);
            TrendPointVO point = new TrendPointVO();
            point.setDate(key);
            if (row == null) {
                points.add(point);
                continue;
            }
            point.setTotal(nz(row.getTotal()));
            point.setSuccessCount(nz(row.getSuccessCount()));
            point.setFailedCount(nz(row.getFailedCount()));
            point.setRunningCount(nz(row.getRunningCount()));
            points.add(point);
        }
        return points;
    }

    public static Double successRate(long success, long failed) {
        long finished = success + failed;
        if (finished <= 0) {
            return null;
        }
        return Math.round(success * 1000.0 / finished) / 10.0;
    }

    public static NamedCountVO named(String name, String label, long count) {
        NamedCountVO vo = new NamedCountVO();
        vo.setName(name);
        vo.setLabel(label);
        vo.setCount(count);
        return vo;
    }

    public static long nz(Long value) {
        return value == null ? 0L : value;
    }
}
