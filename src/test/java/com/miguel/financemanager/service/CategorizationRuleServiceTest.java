package com.miguel.financemanager.service;

import com.miguel.financemanager.dto.RuleRequest;
import com.miguel.financemanager.dto.RuleResponse;
import com.miguel.financemanager.entity.Category;
import com.miguel.financemanager.entity.CategorizationRule;
import com.miguel.financemanager.entity.User;
import com.miguel.financemanager.repository.CategorizationRuleRepository;
import com.miguel.financemanager.repository.CategoryRepository;
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
class CategorizationRuleServiceTest {

    @Mock
    private CategorizationRuleRepository ruleRepository;
    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private CurrentUserService currentUserService;

    @InjectMocks
    private CategorizationRuleService ruleService;

    private User user;

    @BeforeEach
    void setUp() {
        user = User.builder().id(1L).email("miguel@teste.com").passwordHash("hash").build();
        when(currentUserService.getCurrentUser()).thenReturn(user);
    }

    @Test
    void createRule_usesGivenPriorityWhenProvided() {
        Category category = Category.builder().id(2L).user(user).name("Transporte").isDefault(false).build();
        when(categoryRepository.findById(2L)).thenReturn(Optional.of(category));
        when(ruleRepository.save(any(CategorizationRule.class))).thenAnswer(invocation -> {
            CategorizationRule r = invocation.getArgument(0);
            r.setId(50L);
            return r;
        });

        RuleRequest request = new RuleRequest();
        request.setKeyword("uber");
        request.setCategoryId(2L);
        request.setPriority(5);

        RuleResponse response = ruleService.createRule(request);

        assertThat(response.getPriority()).isEqualTo(5);
        assertThat(response.getKeyword()).isEqualTo("uber");
    }

    @Test
    void createRule_appendsAtEndWhenPriorityOmitted() {
        Category category = Category.builder().id(2L).user(user).name("Transporte").isDefault(false).build();
        when(categoryRepository.findById(2L)).thenReturn(Optional.of(category));

        CategorizationRule existing = CategorizationRule.builder()
                .id(1L).user(user).keyword("continente").category(category).priority(3).build();
        when(ruleRepository.findByUserOrderByPriorityAsc(user)).thenReturn(List.of(existing));
        when(ruleRepository.save(any(CategorizationRule.class))).thenAnswer(invocation -> {
            CategorizationRule r = invocation.getArgument(0);
            r.setId(51L);
            return r;
        });

        RuleRequest request = new RuleRequest();
        request.setKeyword("uber");
        request.setCategoryId(2L);
        // priority omitida de propósito

        RuleResponse response = ruleService.createRule(request);

        assertThat(response.getPriority()).isEqualTo(4); // max(3) + 1
    }

    @Test
    void createRule_rejectsCategoryFromAnotherUser() {
        User otherUser = User.builder().id(2L).email("outro@teste.com").passwordHash("hash").build();
        Category category = Category.builder().id(3L).user(otherUser).name("Transporte").isDefault(false).build();
        when(categoryRepository.findById(3L)).thenReturn(Optional.of(category));

        RuleRequest request = new RuleRequest();
        request.setKeyword("uber");
        request.setCategoryId(3L);

        assertThatThrownBy(() -> ruleService.createRule(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("não encontrada");
    }

    @Test
    void deleteRule_rejectsRuleFromAnotherUser() {
        User otherUser = User.builder().id(2L).email("outro@teste.com").passwordHash("hash").build();
        Category category = Category.builder().id(3L).user(otherUser).name("Transporte").isDefault(false).build();
        CategorizationRule rule = CategorizationRule.builder()
                .id(9L).user(otherUser).keyword("x").category(category).priority(0).build();
        when(ruleRepository.findById(9L)).thenReturn(Optional.of(rule));

        assertThatThrownBy(() -> ruleService.deleteRule(9L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("não encontrada");

        verify(ruleRepository, never()).delete(any());
    }
}
