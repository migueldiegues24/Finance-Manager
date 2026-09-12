package com.miguel.financemanager.service;

import com.miguel.financemanager.dto.CategoryRequest;
import com.miguel.financemanager.dto.CategoryResponse;
import com.miguel.financemanager.entity.Category;
import com.miguel.financemanager.entity.User;
import com.miguel.financemanager.repository.CategorizationRuleRepository;
import com.miguel.financemanager.repository.CategoryRepository;
import com.miguel.financemanager.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private CategorizationRuleRepository categorizationRuleRepository;
    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private CurrentUserService currentUserService;

    @InjectMocks
    private CategoryService categoryService;

    private User user;

    @BeforeEach
    void setUp() {
        user = User.builder().id(1L).email("miguel@teste.com").passwordHash("hash").build();
        when(currentUserService.getCurrentUser()).thenReturn(user);
    }

    @Test
    void listCategories_mapsEntitiesToResponses() {
        Category category = Category.builder().id(10L).user(user).name("Alimentação").isDefault(false).build();
        when(categoryRepository.findByUser(user)).thenReturn(List.of(category));

        List<CategoryResponse> result = categoryService.listCategories();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("Alimentação");
        assertThat(result.get(0).isDefaultCategory()).isFalse();
    }

    @Test
    void createCategory_rejectsDuplicateName() {
        when(categoryRepository.existsByUserAndName(user, "Lazer")).thenReturn(true);

        CategoryRequest request = new CategoryRequest();
        request.setName("Lazer");

        assertThatThrownBy(() -> categoryService.createCategory(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Já existe");

        verify(categoryRepository, never()).save(any());
    }

    @Test
    void createCategory_savesNonDefaultCategory() {
        when(categoryRepository.existsByUserAndName(user, "Lazer")).thenReturn(false);
        when(categoryRepository.save(any(Category.class))).thenAnswer(invocation -> {
            Category c = invocation.getArgument(0);
            c.setId(99L);
            return c;
        });

        CategoryRequest request = new CategoryRequest();
        request.setName("Lazer");

        CategoryResponse response = categoryService.createCategory(request);

        assertThat(response.getName()).isEqualTo("Lazer");
        assertThat(response.isDefaultCategory()).isFalse();
    }

    @Test
    void renameCategory_rejectsWhenCategoryIsDefault() {
        Category defaultCategory = Category.builder().id(5L).user(user).name("Sem Categoria").isDefault(true).build();
        when(categoryRepository.findById(5L)).thenReturn(Optional.of(defaultCategory));

        CategoryRequest request = new CategoryRequest();
        request.setName("Outro nome");

        assertThatThrownBy(() -> categoryService.renameCategory(5L, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Sem Categoria");
    }

    @Test
    void renameCategory_rejectsWhenCategoryBelongsToAnotherUser() {
        User otherUser = User.builder().id(2L).email("outro@teste.com").passwordHash("hash").build();
        Category category = Category.builder().id(7L).user(otherUser).name("Alimentação").isDefault(false).build();
        when(categoryRepository.findById(7L)).thenReturn(Optional.of(category));

        CategoryRequest request = new CategoryRequest();
        request.setName("Novo nome");

        assertThatThrownBy(() -> categoryService.renameCategory(7L, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("não encontrada");
    }

    @Test
    void deleteCategory_rejectsWhenCategoryIsDefault() {
        Category defaultCategory = Category.builder().id(5L).user(user).name("Sem Categoria").isDefault(true).build();
        when(categoryRepository.findById(5L)).thenReturn(Optional.of(defaultCategory));

        assertThatThrownBy(() -> categoryService.deleteCategory(5L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Sem Categoria");

        verify(transactionRepository, never()).reassignCategory(any(), any());
    }

    @Test
    void deleteCategory_reassignsTransactionsAndDeletesRulesBeforeDeletingCategory() {
        Category category = Category.builder().id(8L).user(user).name("Lazer").isDefault(false).build();
        Category fallback = Category.builder().id(9L).user(user).name("Sem Categoria").isDefault(true).build();

        when(categoryRepository.findById(8L)).thenReturn(Optional.of(category));
        when(categoryRepository.findByUserAndIsDefaultTrue(user)).thenReturn(Optional.of(fallback));

        categoryService.deleteCategory(8L);

        verify(transactionRepository).reassignCategory(category, fallback);
        verify(categorizationRuleRepository).deleteByCategory(category);
        verify(categoryRepository).delete(category);
    }
}
