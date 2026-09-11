package com.miguel.financemanager.service.parsing;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

public interface StatementParser {
    List<ParsedTransaction> parse(MultipartFile file) throws IOException;
}
