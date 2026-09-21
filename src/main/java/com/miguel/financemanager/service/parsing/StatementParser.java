package com.miguel.financemanager.service.parsing;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

public interface StatementParser {

    // Banco que este parser trata; tem de ser único entre os parsers registados.
    Bank bank();

    List<ParsedTransaction> parse(MultipartFile file) throws IOException;
}
