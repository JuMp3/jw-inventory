package com.gp.jwinventory.dto;

/** imageUrl is null when the publication has no image. */
public record PublicationView(Long id, String code, String title, int quantity, String imageUrl) {
}
