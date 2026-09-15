package com.miguel.financemanager.service;

import com.miguel.financemanager.dto.TransactionResponse;
import com.miguel.financemanager.dto.UpdateTransactionCategoryRequest;
import com.miguel.financemanager.entity.Category;
import com.miguel.financemanager.entity.Transaction;
import com.miguel.financemanager.entity.User;
import com.miguel.financemanager.repository.CategoryRepository;
import com.miguel.financemanager.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final CategoryRepository categoryRepository;
    private final CurrentUserService currentUserService;

    public List<TransactionResponse> listTransactions(YearMonth month) {
        User user = currentUserService.getCurrentUser();

        LocalDate start = month.atDay(1);
        LocalDate end = month.plusMonths(1).atDay(1);

        return transactionRepository
                .findByUserAndTransactionDateGreaterThanEqualAndTransactionDateLessThanOrderByTransactionDateDesc(
                        user, start, end)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public TransactionResponse updateCategory(Long transactionId, UpdateTransactionCategoryRequest request) {
        User user = currentUserService.getCurrentUser();

        Transaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new IllegalArgumentException("Transação não encontrada"));

        if (!transaction.getUser().getId().equals(user.getId())) {
            throw new IllegalArgumentException("Transação não encontrada");
        }

        Category category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new IllegalArgumentException("Categoria não encontrada"));

        if (!category.getUser().getId().equals(user.getId())) {
            throw new IllegalArgumentException("Categoria não encontrada");
        }

        transaction.setCategory(category);
        return toResponse(transactionRepository.save(transaction));
    }

    private TransactionResponse toResponse(Transaction transaction) {
        return new TransactionResponse(
                transaction.getId(),
                transaction.getTransactionDate(),
                transaction.getDescription(),
                transaction.getAmount(),
                transaction.getCategory().getId(),
                transaction.getCategory().getName()
        );
    }
}
