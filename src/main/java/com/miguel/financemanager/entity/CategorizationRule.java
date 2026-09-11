package com.miguel.financemanager.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "categorization_rules")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CategorizationRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 255)
    private String keyword;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    // Desempata quando duas regras batem certo na mesma descrição.
    // Prioridade mais baixa = avaliada primeiro.
    @Column(nullable = false)
    @Builder.Default
    private int priority = 0;
}
