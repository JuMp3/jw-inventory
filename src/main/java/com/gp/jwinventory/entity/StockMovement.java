package com.gp.jwinventory.entity;

import com.gp.jwinventory.enums.MovementType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Append-only log of quantity changes. User and congregation are stored as name
 * snapshots so the history survives user deletion.
 */
@Entity
@Table(name = "stock_movements")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockMovement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "publication_id", nullable = false)
    private Publication publication;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MovementType type;

    @Column(nullable = false)
    private int delta;

    @Column(name = "quantity_after", nullable = false)
    private int quantityAfter;

    @Column(name = "user_name", nullable = false, length = 150)
    private String userName;

    @Column(name = "congregation_name", length = 120)
    private String congregationName;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
