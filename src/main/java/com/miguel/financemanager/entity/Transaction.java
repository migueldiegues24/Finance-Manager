package com.miguel.financemanager.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "transactions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "import_id", nullable = false)
    private StatementImport statementImport;

    // Data usada em toda a app (dashboard, filtros). Nos extratos CGD é a data-valor.
    @Column(name = "transaction_date", nullable = false)
    private LocalDate transactionDate;

    // Data do movimento, quando o extrato a distingue da data-valor (CGD).
    @Column(name = "movement_date")
    private LocalDate movementDate;

    @Column(nullable = false, length = 500)
    private String description;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    // Saldo contabilístico após o movimento, quando o extrato o traz (CGD).
    @Column(name = "balance_after", precision = 12, scale = 2)
    private BigDecimal balanceAfter;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    // Fingerprint de deduplicação (ver TransactionFingerprint). O confirm
    // ignora movimentos cujo hash já existe para o utilizador.
    @Column(nullable = false, length = 64)
    private String hash;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
