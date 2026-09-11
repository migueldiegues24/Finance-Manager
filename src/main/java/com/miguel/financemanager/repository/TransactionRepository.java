package com.miguel.financemanager.repository;

import com.miguel.financemanager.entity.Category;
import com.miguel.financemanager.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    // Usado quando uma categoria é apagada: todas as transações que
    // apontavam para ela passam a apontar para a categoria fallback
    // ("Sem Categoria"), em vez de ficarem órfãs.
    @Modifying
    @Query("UPDATE Transaction t SET t.category = :fallback WHERE t.category = :from")
    void reassignCategory(@Param("from") Category from, @Param("fallback") Category fallback);
}
