package com.miguel.financemanager.service.parsing;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ParsedTransaction(LocalDate date, String description, BigDecimal amount) {
}
