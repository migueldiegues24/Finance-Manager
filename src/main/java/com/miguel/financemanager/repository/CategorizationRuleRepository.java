package com.miguel.financemanager.repository;

import com.miguel.financemanager.entity.Category;
import com.miguel.financemanager.entity.CategorizationRule;
import com.miguel.financemanager.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CategorizationRuleRepository extends JpaRepository<CategorizationRule, Long> {
    void deleteByCategory(Category category);
    List<CategorizationRule> findByUserOrderByPriorityAsc(User user);
}
