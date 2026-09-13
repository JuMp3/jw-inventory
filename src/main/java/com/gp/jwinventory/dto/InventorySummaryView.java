package com.gp.jwinventory.dto;

import java.time.LocalDateTime;

public record InventorySummaryView(Long id, LocalDateTime startedAt, String startedBy, LocalDateTime closedAt,
                                   String closedBy, int countedCount, int differenceCount) {
}
