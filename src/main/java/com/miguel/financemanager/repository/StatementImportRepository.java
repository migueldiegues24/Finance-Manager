package com.miguel.financemanager.repository;

import com.miguel.financemanager.entity.StatementImport;
import com.miguel.financemanager.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StatementImportRepository extends JpaRepository<StatementImport, Long> {
    List<StatementImport> findByUserOrderByIdAsc(User user);
}
