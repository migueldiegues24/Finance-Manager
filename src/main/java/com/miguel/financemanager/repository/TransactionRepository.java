package com.miguel.financemanager.repository;

import com.miguel.financemanager.entity.Category;
import com.miguel.financemanager.entity.Transaction;
import com.miguel.financemanager.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    boolean existsByUserAndHash(User user, String hash);

    // Cada linha: [categoryId (Long), categoryName (String), total (BigDecimal, negativo)]
    // Só soma despesas (amount < 0); "start" incluído, "end" excluído (primeiro dia do mês seguinte).
    @Query("SELECT t.category.id, t.category.name, SUM(t.amount) FROM Transaction t " +
            "WHERE t.user = :user AND t.amount < 0 AND t.transactionDate >= :start AND t.transactionDate < :end " +
            "GROUP BY t.category.id, t.category.name")
    List<Object[]> sumExpensesByCategory(@Param("user") User user, @Param("start") LocalDate start, @Param("end") LocalDate end);

    // Usado quando uma categoria é apagada: todas as transações que
    // apontavam para ela passam a apontar para a categoria fallback
    // ("Sem Categoria"), em vez de ficarem órfãs.
    @Modifying
    @Query("UPDATE Transaction t SET t.category = :fallback WHERE t.category = :from")
    void reassignCategory(@Param("from") Category from, @Param("fallback") Category fallback);
}
