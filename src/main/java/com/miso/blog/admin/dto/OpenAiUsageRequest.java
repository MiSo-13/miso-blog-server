package com.miso.blog.admin.dto;

import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

public class OpenAiUsageRequest {
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate startDate;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate endDate;

    private String bucketWidth;
    private String groupBy;

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }

    public String getBucketWidth() {
        return bucketWidth;
    }

    public void setBucketWidth(String bucketWidth) {
        this.bucketWidth = bucketWidth;
    }

    public String getGroupBy() {
        return groupBy;
    }

    public void setGroupBy(String groupBy) {
        this.groupBy = groupBy;
    }
}
