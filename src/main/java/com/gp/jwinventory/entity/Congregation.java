package com.gp.jwinventory.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "congregations")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Congregation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
