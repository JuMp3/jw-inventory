package com.gp.jwinventory.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "inventory_items")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private InventorySession session;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "publication_id", nullable = false)
    private Publication publication;

    @Column(name = "counted_quantity", nullable = false)
    private int countedQuantity;

    /** System quantity at closing time; null while the session is open. */
    @Column(name = "expected_quantity")
    private Integer expectedQuantity;

    @Column(name = "counted_by", nullable = false, length = 150)
    private String countedBy;

    @Column(name = "counted_at", nullable = false)
    private LocalDateTime countedAt;
}
