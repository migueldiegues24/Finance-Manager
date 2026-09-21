package com.miguel.financemanager.service.parsing;

import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.List;

// Extrato "Consulta de movimentos" da CGD. A app chama-lhe XLS mas é um
// CSV UTF-8 (com BOM) separado por ';', com um preâmbulo de várias linhas
// antes do cabeçalho e movimentos do mais recente para o mais antigo.
// Por isso a validação é feita pelo cabeçalho e nunca pela extensão.
@Component
public class CgdCsvStatementParser implements StatementParser {

    static final List<String> HEADER = List.of(
            "Data mov.", "Data-valor", "Descrição", "Montante", "Saldo contabilístico após movimento");

    private static final char BOM = '﻿';
    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd-MM-uuuu").withResolverStyle(ResolverStyle.STRICT);

    private record Row(int lineNumber, ParsedTransaction transaction) {
    }

    @Override
    public Bank bank() {
        return Bank.CGD;
    }

    @Override
    public List<ParsedTransaction> parse(MultipartFile file) throws IOException {
        List<Row> rows = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            int lineNumber = skipToHeader(reader);

            String line;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.isBlank()) {
                    continue;
                }
                rows.add(new Row(lineNumber, parseRow(line, lineNumber)));
            }
        }

        if (rows.isEmpty()) {
            throw new IOException("O extrato CGD não tem movimentos");
        }

        // A cadeia é validada na ordem original do ficheiro (mais recente
        // primeiro); só depois a lista é invertida para ordem cronológica.
        validateBalanceChain(rows);

        return rows.reversed().stream().map(Row::transaction).toList();
    }

    // Devolve o número (1-based) da linha do cabeçalho.
    private int skipToHeader(BufferedReader reader) throws IOException {
        int lineNumber = 0;
        String line;
        while ((line = reader.readLine()) != null) {
            lineNumber++;
            if (isHeader(line)) {
                return lineNumber;
            }
        }
        throw new IOException("O ficheiro não parece ser um extrato CGD: cabeçalho '"
                + String.join(";", HEADER) + "' não encontrado");
    }

    private boolean isHeader(String line) {
        String[] columns = stripBom(line).split(";", -1);
        if (columns.length < HEADER.size()) {
            return false;
        }
        for (int i = 0; i < HEADER.size(); i++) {
            if (!columns[i].trim().equals(HEADER.get(i))) {
                return false;
            }
        }
        return true;
    }

    private ParsedTransaction parseRow(String line, int lineNumber) throws IOException {
        String[] columns = line.split(";", -1);
        if (columns.length < HEADER.size()) {
            throw new IOException("Linha " + lineNumber + ": esperadas " + HEADER.size()
                    + " colunas, encontradas " + columns.length);
        }

        LocalDate movementDate = parseDate(columns[0], "Data mov.", lineNumber);
        LocalDate valueDate = parseDate(columns[1], "Data-valor", lineNumber);
        String description = columns[2].trim();
        BigDecimal amount = parseAmount(columns[3], "Montante", lineNumber);
        BigDecimal balance = parseAmount(columns[4], "Saldo", lineNumber);

        if (description.isEmpty()) {
            throw new IOException("Linha " + lineNumber + ": descrição vazia");
        }

        return new ParsedTransaction(valueDate, movementDate, description, amount, balance);
    }

    private LocalDate parseDate(String value, String column, int lineNumber) throws IOException {
        try {
            return LocalDate.parse(value.trim(), DATE_FORMAT);
        } catch (DateTimeParseException e) {
            throw new IOException("Linha " + lineNumber + ": " + column + " inválida '" + value.trim()
                    + "' (esperado dd-MM-aaaa)");
        }
    }

    // Formato português: vírgula decimal e ponto como separador de milhares
    // (ex.: "-1.234,56").
    static BigDecimal parseAmount(String value, String column, int lineNumber) throws IOException {
        String cleaned = value.trim()
                .replace(" ", "")
                .replace(" ", "")
                .replace(".", "")
                .replace(',', '.');
        try {
            return new BigDecimal(cleaned);
        } catch (NumberFormatException e) {
            throw new IOException("Linha " + lineNumber + ": " + column + " inválido '" + value.trim() + "'");
        }
    }

    // Cada saldo tem de ser o saldo do movimento seguinte no ficheiro (o
    // anterior no tempo) mais o montante da própria linha.
    private void validateBalanceChain(List<Row> rows) throws IOException {
        for (int i = 0; i < rows.size() - 1; i++) {
            Row current = rows.get(i);
            Row older = rows.get(i + 1);
            BigDecimal expected = older.transaction().balanceAfter().add(current.transaction().amount());

            if (current.transaction().balanceAfter().compareTo(expected) != 0) {
                throw new IOException("Saldos inconsistentes na linha " + current.lineNumber()
                        + ": esperado " + expected.toPlainString()
                        + " (saldo " + older.transaction().balanceAfter().toPlainString()
                        + " da linha " + older.lineNumber()
                        + " + montante " + current.transaction().amount().toPlainString()
                        + "), encontrado " + current.transaction().balanceAfter().toPlainString()
                        + ". O extrato parece incompleto: se foi exportado com filtros (tipo de movimento,"
                        + " montante ou descrição), exporta-o de novo sem filtros.");
            }
        }
    }

    private static String stripBom(String line) {
        return !line.isEmpty() && line.charAt(0) == BOM ? line.substring(1) : line;
    }
}
