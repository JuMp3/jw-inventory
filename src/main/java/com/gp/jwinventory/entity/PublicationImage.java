package com.gp.jwinventory.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "publication_images")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PublicationImage {

    @Id
    @Column(name = "publication_id")
    private Long publicationId;

    @Column(name = "content_type", nullable = false, length = 50)
    private String contentType;

    @Column(nullable = false, columnDefinition = "BYTEA")
    private byte[] data;
}
