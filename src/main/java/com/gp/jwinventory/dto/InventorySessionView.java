package com.gp.jwinventory.dto;

import com.gp.jwinventory.enums.InventoryStatus;

import java.time.LocalDateTime;
import java.util.List;

public record InventorySessionView(Long id, InventoryStatus status, LocalDateTime startedAt, String startedBy,
                                   LocalDateTime closedAt, String closedBy, List<InventoryRowView> rows,
                                   long countedCount, long differenceCount) {

    public boolean isOpen() {
        return status == InventoryStatus.OPEN;
    }
}
