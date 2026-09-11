package com.miguel.financemanager.service;

import com.miguel.financemanager.dto.CategoryRequest;
import com.miguel.financemanager.dto.CategoryResponse;
import com.miguel.financemanager.entity.Category;
import com.miguel.financemanager.entity.User;
import com.miguel.financemanager.repository.CategorizationRuleRepository;
import com.miguel.financemanager.repository.CategoryRepository;
import com.miguel.financemanager.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final CategorizationRuleRepository categorizationRuleRepository;
    private final TransactionRepository transactionRepository;
    private final CurrentUserService currentUserService;

    public List<CategoryResponse> listCategories() {
        User user = currentUserService.getCurrentUser();
        return categoryRepository.findByUser(user).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public CategoryResponse createCategory(CategoryRequest request) {
        User user = currentUserService.getCurrentUser();

        if (categoryRepository.existsByUserAndName(user, request.getName())) {
            throw new IllegalArgumentException("Já existe uma categoria com este nome");
        }

        Category category = Category.builder()
                .user(user)
                .name(request.getName())
                .isDefault(false)
                .build();

        return toResponse(categoryRepository.save(category));
    }

    @Transactional
    public CategoryResponse renameCategory(Long categoryId, CategoryRequest request) {
        User user = currentUserService.getCurrentUser();
        Category category = getOwnedCategory(categoryId, user);

        if (category.isDefault()) {
            throw new IllegalArgumentException("Não é possível editar a categoria \"Sem Categoria\"");
        }

        if (!category.getName().equals(request.getName())
                && categoryRepository.existsByUserAndName(user, request.getName())) {
            throw new IllegalArgumentException("Já existe uma categoria com este nome");
        }

        category.setName(request.getName());
        return toResponse(categoryRepository.save(category));
    }

    @Transactional
    public void deleteCategory(Long categoryId) {
        User user = currentUserService.getCurrentUser();
        Category category = getOwnedCategory(categoryId, user);

        if (category.isDefault()) {
            throw new IllegalArgumentException("Não é possível apagar a categoria \"Sem Categoria\"");
        }

        Category fallback = categoryRepository.findByUserAndIsDefaultTrue(user)
                .orElseThrow(() -> new IllegalStateException("Categoria \"Sem Categoria\" não encontrada para o utilizador"));

        // Transações ficam com a categoria fallback; regras que apontavam
        // para esta categoria são apagadas, tal como decidido.
        transactionRepository.reassignCategory(category, fallback);
        categorizationRuleRepository.deleteByCategory(category);

        categoryRepository.delete(category);
    }

    private Category getOwnedCategory(Long categoryId, User user) {
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new IllegalArgumentException("Categoria não encontrada"));

        if (!category.getUser().getId().equals(user.getId())) {
            throw new IllegalArgumentException("Categoria não encontrada");
        }

        return category;
    }

    private CategoryResponse toResponse(Category category) {
        return new CategoryResponse(category.getId(), category.getName(), category.isDefault());
    }
}
