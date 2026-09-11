package com.miguel.financemanager.repository;

import com.miguel.financemanager.entity.StatementImport;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StatementImportRepository extends JpaRepository<StatementImport, Long> {
}
