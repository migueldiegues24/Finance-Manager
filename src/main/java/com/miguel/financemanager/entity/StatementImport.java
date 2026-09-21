package com.miguel.financemanager.entity;

import jakarta.persistence.*;
import com.miguel.financemanager.service.parsing.Bank;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "statement_imports")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StatementImport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 255)
    private String filename;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private Bank bank = Bank.GENERIC;

    @Column(name = "imported_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime importedAt = LocalDateTime.now();
}
