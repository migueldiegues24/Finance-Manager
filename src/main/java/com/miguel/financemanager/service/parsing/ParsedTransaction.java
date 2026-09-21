package com.miguel.financemanager.service.parsing;

import java.math.BigDecimal;
import java.time.LocalDate;

// date é a data usada no resto da app (na CGD, a data-valor).
// movementDate e balanceAfter só existem em extratos que os trazem;
// no CSV genérico ficam a null.
public record ParsedTransaction(LocalDate date, LocalDate movementDate, String description,
                                BigDecimal amount, BigDecimal balanceAfter) {

    public ParsedTransaction(LocalDate date, String description, BigDecimal amount) {
        this(date, null, description, amount, null);
    }
}
