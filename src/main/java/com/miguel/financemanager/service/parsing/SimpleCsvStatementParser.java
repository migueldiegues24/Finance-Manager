package com.miguel.financemanager.service.parsing;

import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

// Parser provisório para a v1: assume um CSV simples com cabeçalho
// "date,description,amount" e datas no formato ISO (yyyy-MM-dd).
// Quando houver um extrato real de um banco português para testar,
// este é o único ficheiro a mudar (ou a substituir por outra
// implementação de StatementParser); o resto do fluxo de import
// mantém-se igual.
@Component
public class SimpleCsvStatementParser implements StatementParser {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;

    @Override
    public List<ParsedTransaction> parse(MultipartFile file) throws IOException {
        List<ParsedTransaction> transactions = new ArrayList<>();

        try (CSVReader reader = new CSVReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String[] header = reader.readNext(); // salta a linha de cabeçalho
            if (header == null) {
                return transactions;
            }

            String[] line;
            while ((line = reader.readNext()) != null) {
                if (line.length < 3) {
                    continue;
                }

                LocalDate date = LocalDate.parse(line[0].trim(), DATE_FORMAT);
                String description = line[1].trim();
                BigDecimal amount = new BigDecimal(line[2].trim());

                transactions.add(new ParsedTransaction(date, description, amount));
            }
        } catch (CsvValidationException e) {
            throw new IOException("CSV inválido: " + e.getMessage(), e);
        }

        return transactions;
    }
}
