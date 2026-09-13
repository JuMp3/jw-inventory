package com.gp.jwinventory.dto;

import com.gp.jwinventory.enums.MovementType;

import java.time.LocalDateTime;

public record MovementView(LocalDateTime createdAt, Long publicationId, String publicationCode, String publicationTitle,
                           MovementType type, int delta, int quantityAfter, String userName, String congregationName) {
}
