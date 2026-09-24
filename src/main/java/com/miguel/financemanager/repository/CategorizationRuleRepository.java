package com.miguel.financemanager.repository;

import com.miguel.financemanager.entity.Category;
import com.miguel.financemanager.entity.CategorizationRule;
import com.miguel.financemanager.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CategorizationRuleRepository extends JpaRepository<CategorizationRule, Long> {
    void deleteByCategory(Category category);
    List<CategorizationRule> findByUserOrderByPriorityAsc(User user);

    // Só para apagar a conta (AccountService.deleteAccount).
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM CategorizationRule x WHERE x.user = :user")
    int deleteAllByUser(@Param("user") User user);
}
