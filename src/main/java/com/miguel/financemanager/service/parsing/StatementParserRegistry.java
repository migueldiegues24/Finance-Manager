package com.miguel.financemanager.service.parsing;

import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

// Junta todos os StatementParser do contexto e escolhe um por banco.
// Falha no arranque se dois parsers declararem o mesmo banco.
@Component
public class StatementParserRegistry {

    private final Map<Bank, StatementParser> parsers = new EnumMap<>(Bank.class);

    public StatementParserRegistry(List<StatementParser> parsers) {
        for (StatementParser parser : parsers) {
            StatementParser previous = this.parsers.putIfAbsent(parser.bank(), parser);
            if (previous != null) {
                throw new IllegalStateException("Dois parsers para o banco " + parser.bank() + ": "
                        + previous.getClass().getSimpleName() + " e " + parser.getClass().getSimpleName());
            }
        }
    }

    public StatementParser forBank(Bank bank) {
        StatementParser parser = parsers.get(bank);
        if (parser == null) {
            throw new IllegalArgumentException("Não existe parser para o banco " + bank);
        }
        return parser;
    }
}
