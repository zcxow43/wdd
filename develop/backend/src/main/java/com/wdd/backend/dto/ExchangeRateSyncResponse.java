package com.wdd.backend.dto;

import java.time.LocalDateTime;
import java.util.List;

public class ExchangeRateSyncResponse {

    private LocalDateTime syncedAt;
    private List<ExchangeRateSyncUpdatedItem> updated;
    private List<ExchangeRateSyncSkippedItem> skipped;

    public ExchangeRateSyncResponse() {
    }

    public ExchangeRateSyncResponse(LocalDateTime syncedAt, List<ExchangeRateSyncUpdatedItem> updated,
            List<ExchangeRateSyncSkippedItem> skipped) {
        this.syncedAt = syncedAt;
        this.updated = updated;
        this.skipped = skipped;
    }

    public LocalDateTime getSyncedAt() {
        return syncedAt;
    }

    public void setSyncedAt(LocalDateTime syncedAt) {
        this.syncedAt = syncedAt;
    }

    public List<ExchangeRateSyncUpdatedItem> getUpdated() {
        return updated;
    }

    public void setUpdated(List<ExchangeRateSyncUpdatedItem> updated) {
        this.updated = updated;
    }

    public List<ExchangeRateSyncSkippedItem> getSkipped() {
        return skipped;
    }

    public void setSkipped(List<ExchangeRateSyncSkippedItem> skipped) {
        this.skipped = skipped;
    }
}
