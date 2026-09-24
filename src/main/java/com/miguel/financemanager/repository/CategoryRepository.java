package com.miguel.financemanager.repository;

import com.miguel.financemanager.entity.Category;
import com.miguel.financemanager.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CategoryRepository extends JpaRepository<Category, Long> {
    // Categorias pela ordem de criação, com a protegida ("Sem Categoria")
    // sempre no fim (false < true em isDefault).
    List<Category> findByUserOrderByIsDefaultAscIdAsc(User user);
    boolean existsByUserAndName(User user, String name);
    Optional<Category> findByUserAndName(User user, String name);
    Optional<Category> findByUserAndIsDefaultTrue(User user);

    // Só para apagar a conta (AccountService.deleteAccount).
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM Category x WHERE x.user = :user")
    int deleteAllByUser(@Param("user") User user);
}
