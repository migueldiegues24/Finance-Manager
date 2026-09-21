package com.miguel.financemanager.service.parsing;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StatementParserRegistryTest {

    private final SimpleCsvStatementParser generic = new SimpleCsvStatementParser();
    private final CgdCsvStatementParser cgd = new CgdCsvStatementParser();

    @Test
    void forBank_returnsParserDeclaredForThatBank() {
        StatementParserRegistry registry = new StatementParserRegistry(List.of(generic, cgd));

        assertThat(registry.forBank(Bank.GENERIC)).isSameAs(generic);
        assertThat(registry.forBank(Bank.CGD)).isSameAs(cgd);
    }

    @Test
    void forBank_throwsWhenNoParserIsRegistered() {
        StatementParserRegistry registry = new StatementParserRegistry(List.of(generic));

        assertThatThrownBy(() -> registry.forBank(Bank.CGD))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("CGD");
    }

    @Test
    void constructor_failsWhenTwoParsersClaimTheSameBank() {
        assertThatThrownBy(() -> new StatementParserRegistry(List.of(generic, new SimpleCsvStatementParser())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("GENERIC");
    }

    @Test
    void bankFromParam_defaultsToGenericAndIsCaseInsensitive() {
        assertThat(Bank.fromParam(null)).isEqualTo(Bank.GENERIC);
        assertThat(Bank.fromParam(" ")).isEqualTo(Bank.GENERIC);
        assertThat(Bank.fromParam("cgd")).isEqualTo(Bank.CGD);
    }

    @Test
    void bankFromParam_rejectsUnknownBankListingValidValues() {
        assertThatThrownBy(() -> Bank.fromParam("xpto"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("xpto")
                .hasMessageContaining("CGD");
    }
}
