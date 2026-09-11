package com.miguel.financemanager.repository;

import com.miguel.financemanager.entity.Category;
import com.miguel.financemanager.entity.CategorizationRule;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategorizationRuleRepository extends JpaRepository<CategorizationRule, Long> {
    void deleteByCategory(Category category);
}
