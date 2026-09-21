package com.miguel.financemanager.repository;

import com.miguel.financemanager.entity.Category;
import com.miguel.financemanager.entity.StatementImport;
import com.miguel.financemanager.entity.Transaction;
import com.miguel.financemanager.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TransactionUniqueHashTest {

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private CategoryRepository categoryRepository;
    @Autowired
    private StatementImportRepository statementImportRepository;
    @Autowired
    private TransactionRepository transactionRepository;

    private User alice;
    private User bob;

    @BeforeEach
    void setUp() {
        alice = userRepository.save(User.builder().email("alice@teste.com").passwordHash("x").build());
        bob = userRepository.save(User.builder().email("bob@teste.com").passwordHash("x").build());
    }

    private Transaction transaction(User user, String hash) {
        Category category = categoryRepository.save(Category.builder().user(user).name("Cat " + hash + user.getId()).build());
        StatementImport statementImport = statementImportRepository.save(
                StatementImport.builder().user(user).filename("x.csv").build());
        return Transaction.builder()
                .user(user)
                .statementImport(statementImport)
                .transactionDate(LocalDate.of(2026, 9, 1))
                .description("Cafe")
                .amount(new BigDecimal("-1.20"))
                .category(category)
                .hash(hash)
                .build();
    }

    @Test
    void sameHashTwiceForSameUserViolatesUniqueIndex() {
        transactionRepository.saveAndFlush(transaction(alice, "same-hash"));

        assertThatThrownBy(() -> transactionRepository.saveAndFlush(transaction(alice, "same-hash")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void sameHashForDifferentUsersIsAllowed() {
        transactionRepository.saveAndFlush(transaction(alice, "same-hash"));

        assertThatCode(() -> transactionRepository.saveAndFlush(transaction(bob, "same-hash")))
                .doesNotThrowAnyException();
    }
}
