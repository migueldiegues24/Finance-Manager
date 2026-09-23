package com.miguel.financemanager.service.parsing;

import com.miguel.financemanager.exception.PayloadTooLargeException;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

public interface StatementParser {

    // Máximo de movimentos por extrato (~9x as 536 linhas de um extrato real
    // de um ano). Verificado durante a leitura, para um ficheiro com milhares
    // de linhas não chegar a ser todo lido para memória nem consultado na BD.
    int MAX_TRANSACTIONS = 5000;

    static PayloadTooLargeException tooManyTransactions() {
        return new PayloadTooLargeException("O extrato tem demasiados movimentos (máximo " + MAX_TRANSACTIONS + ").");
    }

    // Banco que este parser trata; tem de ser único entre os parsers registados.
    Bank bank();

    List<ParsedTransaction> parse(MultipartFile file) throws IOException;
}
