package com.miguel.financemanager.repository;

import com.miguel.financemanager.entity.Category;
import com.miguel.financemanager.entity.Transaction;
import com.miguel.financemanager.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    boolean existsByUserAndHash(User user, String hash);

    List<Transaction> findByUserOrderByTransactionDateAscIdAsc(User user);

    List<Transaction> findByUserAndTransactionDateGreaterThanEqualAndTransactionDateLessThanOrderByTransactionDateDesc(
            User user, LocalDate start, LocalDate end);

    // Cada linha: [categoryId (Long), categoryName (String), total (BigDecimal, negativo)]
    // Só soma despesas (amount < 0); "start" incluído, "end" excluído (primeiro dia do mês seguinte).
    @Query("SELECT t.category.id, t.category.name, SUM(t.amount) FROM Transaction t " +
            "WHERE t.user = :user AND t.amount < 0 AND t.transactionDate >= :start AND t.transactionDate < :end " +
            "GROUP BY t.category.id, t.category.name")
    List<Object[]> sumExpensesByCategory(@Param("user") User user, @Param("start") LocalDate start, @Param("end") LocalDate end);

    // Hashes dos movimentos do utilizador com os mesmos campos que entram na
    // fingerprint. Data do movimento e saldo null só coincidem com null. Os
    // CAST são necessários no Postgres: sem eles, um parâmetro null em
    // "IS NULL" não tem tipo ("could not determine data type of parameter").
    @Query("SELECT t.hash FROM Transaction t WHERE t.user = :user " +
            "AND t.transactionDate = :date AND t.description = :description AND t.amount = :amount " +
            "AND (t.movementDate = :movementDate OR (CAST(:movementDate AS LocalDate) IS NULL AND t.movementDate IS NULL)) " +
            "AND (t.balanceAfter = :balanceAfter OR (CAST(:balanceAfter AS BigDecimal) IS NULL AND t.balanceAfter IS NULL))")
    List<String> findHashesWithSameFields(@Param("user") User user, @Param("date") LocalDate date,
                                          @Param("movementDate") LocalDate movementDate,
                                          @Param("description") String description,
                                          @Param("amount") BigDecimal amount,
                                          @Param("balanceAfter") BigDecimal balanceAfter);

    // Soma das receitas (amount > 0) no intervalo; null se não houver nenhuma.
    @Query("SELECT SUM(t.amount) FROM Transaction t " +
            "WHERE t.user = :user AND t.amount > 0 AND t.transactionDate >= :start AND t.transactionDate < :end")
    BigDecimal sumIncome(@Param("user") User user, @Param("start") LocalDate start, @Param("end") LocalDate end);

    // Soma das despesas (amount < 0, portanto negativa) no intervalo; null se não houver nenhuma.
    @Query("SELECT SUM(t.amount) FROM Transaction t " +
            "WHERE t.user = :user AND t.amount < 0 AND t.transactionDate >= :start AND t.transactionDate < :end")
    BigDecimal sumExpenses(@Param("user") User user, @Param("start") LocalDate start, @Param("end") LocalDate end);

    // Cada linha: [ano (Integer), mês (Integer)] com pelo menos uma transação do utilizador.
    @Query("SELECT DISTINCT YEAR(t.transactionDate), MONTH(t.transactionDate) FROM Transaction t WHERE t.user = :user")
    List<Object[]> findDistinctYearMonths(@Param("user") User user);

    // Usado quando uma categoria é apagada: todas as transações que
    // apontavam para ela passam a apontar para a categoria fallback
    // ("Sem Categoria"), em vez de ficarem órfãs.
    @Modifying
    @Query("UPDATE Transaction t SET t.category = :fallback WHERE t.category = :from")
    void reassignCategory(@Param("from") Category from, @Param("fallback") Category fallback);
}
