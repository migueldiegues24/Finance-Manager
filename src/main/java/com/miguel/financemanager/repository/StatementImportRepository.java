package com.miguel.financemanager.repository;

import com.miguel.financemanager.entity.StatementImport;
import com.miguel.financemanager.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface StatementImportRepository extends JpaRepository<StatementImport, Long> {
    List<StatementImport> findByUserOrderByIdAsc(User user);

    // Só para apagar a conta (AccountService.deleteAccount).
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM StatementImport x WHERE x.user = :user")
    int deleteAllByUser(@Param("user") User user);
}
