package com.miguel.financemanager.service;

import com.miguel.financemanager.dto.ConfirmImportRequest;
import com.miguel.financemanager.dto.ImportSummaryResponse;
import com.miguel.financemanager.dto.ParseImportResponse;
import com.miguel.financemanager.dto.ParsedTransactionResponse;
import com.miguel.financemanager.entity.User;
import com.miguel.financemanager.exception.ImportConflictException;
import com.miguel.financemanager.repository.TransactionRepository;
import com.miguel.financemanager.service.parsing.Bank;
import com.miguel.financemanager.service.parsing.ParsedTransaction;
import com.miguel.financemanager.service.parsing.StatementParserRegistry;
import com.miguel.financemanager.service.util.TransactionFingerprint;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ImportService {

    private final StatementParserRegistry parserRegistry;
    private final TransactionRepository transactionRepository;
    private final ImportWriter importWriter;
    private final CurrentUserService currentUserService;

    // Não persiste nada. O frontend mostra esta lista numa página de
    // revisão, onde o utilizador remove o que não quer antes de confirmar.
    public ParseImportResponse parseStatement(MultipartFile file, Bank bank) {
        User user = currentUserService.getCurrentUser();

        List<ParsedTransaction> parsed;
        try {
            parsed = parserRegistry.forBank(bank).parse(file);
        } catch (IOException e) {
            throw new IllegalArgumentException("Não foi possível ler o ficheiro: " + e.getMessage());
        }

        // Movimentos idênticos no mesmo ficheiro recebem ocorrências 0, 1, 2...
        // pela ordem em que aparecem, para não colidirem entre si.
        Map<String, Integer> seen = new HashMap<>();
        List<ParsedTransactionResponse> response = new ArrayList<>();
        for (ParsedTransaction t : parsed) {
            String baseHash = fingerprint(user, t, 0);
            int occurrence = seen.merge(baseHash, 1, Integer::sum) - 1;
            String hash = occurrence == 0 ? baseHash : fingerprint(user, t, occurrence);
            boolean duplicate = transactionRepository.existsByUserAndHash(user, hash);
            response.add(new ParsedTransactionResponse(t.date(), t.movementDate(), t.description(), t.amount(),
                    t.balanceAfter(), occurrence, hash, duplicate));
        }

        return new ParseImportResponse(file.getOriginalFilename(), response);
    }

    // Sem @Transactional de propósito: cada tentativa corre numa transação
    // própria dentro do ImportWriter. Se outro confirm gravar os mesmos
    // movimentos ao mesmo tempo, a primeira tentativa viola o índice único e
    // é revertida por inteiro; a segunda já vê essas linhas e ignora-as.
    public ImportSummaryResponse confirmImport(ConfirmImportRequest request) {
        User user = currentUserService.getCurrentUser();
        try {
            return importWriter.write(user, request);
        } catch (DataIntegrityViolationException first) {
            log.info("Conflito ao gravar a importação do utilizador {}; a repetir", user.getId());
            try {
                return importWriter.write(user, request);
            } catch (DataIntegrityViolationException second) {
                throw new ImportConflictException(
                        "Outra importação gravou os mesmos movimentos ao mesmo tempo. Tenta de novo.", second);
            }
        }
    }

    private String fingerprint(User user, ParsedTransaction t, int occurrence) {
        return TransactionFingerprint.compute(user.getId(), t.date(), t.movementDate(), t.description(),
                t.amount(), t.balanceAfter(), occurrence);
    }
}
