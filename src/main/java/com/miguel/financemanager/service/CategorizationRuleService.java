package com.miguel.financemanager.service;

import com.miguel.financemanager.dto.RuleRequest;
import com.miguel.financemanager.dto.RuleResponse;
import com.miguel.financemanager.entity.Category;
import com.miguel.financemanager.entity.CategorizationRule;
import com.miguel.financemanager.entity.User;
import com.miguel.financemanager.repository.CategorizationRuleRepository;
import com.miguel.financemanager.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CategorizationRuleService {

    private final CategorizationRuleRepository ruleRepository;
    private final CategoryRepository categoryRepository;
    private final CurrentUserService currentUserService;

    public List<RuleResponse> listRules() {
        User user = currentUserService.getCurrentUser();
        return ruleRepository.findByUserOrderByPriorityAsc(user).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public RuleResponse createRule(RuleRequest request) {
        User user = currentUserService.getCurrentUser();
        Category category = getOwnedCategory(request.getCategoryId(), user);

        int priority = request.getPriority() != null
                ? request.getPriority()
                : nextPriority(user);

        CategorizationRule rule = CategorizationRule.builder()
                .user(user)
                .keyword(request.getKeyword().trim())
                .category(category)
                .priority(priority)
                .build();

        return toResponse(ruleRepository.save(rule));
    }

    @Transactional
    public void deleteRule(Long ruleId) {
        User user = currentUserService.getCurrentUser();
        CategorizationRule rule = ruleRepository.findById(ruleId)
                .orElseThrow(() -> new IllegalArgumentException("Regra não encontrada"));

        if (!rule.getUser().getId().equals(user.getId())) {
            throw new IllegalArgumentException("Regra não encontrada");
        }

        ruleRepository.delete(rule);
    }

    private Category getOwnedCategory(Long categoryId, User user) {
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new IllegalArgumentException("Categoria não encontrada"));

        if (!category.getUser().getId().equals(user.getId())) {
            throw new IllegalArgumentException("Categoria não encontrada");
        }

        return category;
    }

    private int nextPriority(User user) {
        return ruleRepository.findByUserOrderByPriorityAsc(user).stream()
                .mapToInt(CategorizationRule::getPriority)
                .max()
                .orElse(-1) + 1;
    }

    private RuleResponse toResponse(CategorizationRule rule) {
        return new RuleResponse(
                rule.getId(),
                rule.getKeyword(),
                rule.getCategory().getId(),
                rule.getCategory().getName(),
                rule.getPriority()
        );
    }
}
