package com.gp.jwinventory.dto;

/**
 * expected: current system quantity (open session) or quantity at closing (closed session).
 * counted and difference are null when the publication has not been counted.
 */
public record InventoryRowView(Long publicationId, String code, String title, String imageUrl,
                               int expected, Integer counted, Integer difference) {

    public static InventoryRowView of(Long publicationId, String code, String title, String imageUrl,
                                      int expected, Integer counted) {
        return new InventoryRowView(publicationId, code, title, imageUrl, expected, counted,
                counted == null ? null : counted - expected);
    }
}
