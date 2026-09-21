package com.miguel.financemanager.service.parsing;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CgdCsvStatementParserTest {

    private final CgdCsvStatementParser parser = new CgdCsvStatementParser();

    // Extrato sintético: 14 linhas de preâmbulo, cabeçalho na linha 15 e 7
    // movimentos (linhas 16-22) do mais recente para o mais antigo.
    static String syntheticCsv() throws IOException {
        try (InputStream in = CgdCsvStatementParserTest.class.getResourceAsStream("/statements/cgd_sintetico.csv")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static MockMultipartFile file(String name, String content) {
        return new MockMultipartFile("file", name, "text/csv", content.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void bank_isCgd() {
        assertThat(parser.bank()).isEqualTo(Bank.CGD);
    }

    @Test
    void parse_readsAllMovementsInChronologicalOrder() throws IOException {
        List<ParsedTransaction> result = parser.parse(file("extrato.csv", syntheticCsv()));

        assertThat(result).hasSize(7);
        assertThat(result.get(0).description()).isEqualTo("TRF MBWAY JOAO FICTICIO");
        assertThat(result.get(6).description()).isEqualTo("COMPRAS C.DEB SUPERMERCADO EXEMPLO");
    }

    @Test
    void parse_usesValueDateAsDateAndKeepsMovementDate() throws IOException {
        ParsedTransaction coffee = parser.parse(file("extrato.csv", syntheticCsv())).get(1);

        assertThat(coffee.date()).isEqualTo(LocalDate.of(2026, 8, 1));
        assertThat(coffee.movementDate()).isEqualTo(LocalDate.of(2026, 8, 2));
    }

    @Test
    void parse_handlesDecimalCommaThousandsDotAndBalance() throws IOException {
        List<ParsedTransaction> result = parser.parse(file("extrato.csv", syntheticCsv()));

        ParsedTransaction salary = result.get(3);
        assertThat(salary.amount()).isEqualByComparingTo(new BigDecimal("1234.56"));
        assertThat(salary.balanceAfter()).isEqualByComparingTo(new BigDecimal("2282.16"));

        ParsedTransaction coffee = result.get(1);
        assertThat(coffee.amount()).isEqualByComparingTo(new BigDecimal("-1.20"));
        assertThat(coffee.balanceAfter()).isEqualByComparingTo(new BigDecimal("1048.80"));
    }

    @Test
    void parse_keepsAccentsInDescription() throws IOException {
        assertThat(parser.parse(file("extrato.csv", syntheticCsv())).get(4).description())
                .isEqualTo("Pagamento Serviços Água");
    }

    @Test
    void parse_acceptsFileNamedXlsBecauseValidationIsByHeader() throws IOException {
        assertThat(parser.parse(file("Consulta_de_movimentos.xls", syntheticCsv()))).hasSize(7);
    }

    @Test
    void parse_acceptsWindowsLineEndings() throws IOException {
        assertThat(parser.parse(file("extrato.csv", syntheticCsv().replace("\n", "\r\n")))).hasSize(7);
    }

    @Test
    void parse_rejectsFileWithoutCgdHeader() {
        String genericCsv = "date,description,amount\n2026-09-01,Uber,-8.50\n";

        assertThatThrownBy(() -> parser.parse(file("extrato.csv", genericCsv)))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("não parece ser um extrato CGD")
                .hasMessageContaining("Data mov.;Data-valor");
    }

    @Test
    void parse_rejectsStatementWithHeaderButNoMovements() throws IOException {
        String headerOnly = syntheticCsv().lines().limit(15).reduce("", (a, b) -> a + b + "\n");

        assertThatThrownBy(() -> parser.parse(file("extrato.csv", headerOnly)))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("não tem movimentos");
    }

    @Test
    void parse_rejectsBrokenBalanceChainMentioningLineAndFilters() throws IOException {
        // Remove o movimento "TRF P2P" (linha 17), como faria um extrato filtrado:
        // a linha 16 deixa de bater certo com o saldo da linha seguinte.
        String filtered = syntheticCsv().replace("06-08-2026;06-08-2026;TRF P2P MARIA EXEMPLO;-100,00;2.158,71\n", "");

        assertThatThrownBy(() -> parser.parse(file("extrato.csv", filtered)))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("linha 16")
                .hasMessageContaining("esperado 2200.00")
                .hasMessageContaining("encontrado 2100.00")
                .hasMessageContaining("filtros");
    }

    @Test
    void parse_rejectsInvalidDateWithLineNumber() throws IOException {
        String broken = syntheticCsv().replace("08-08-2026;07-08-2026", "2026-08-08;07-08-2026");

        assertThatThrownBy(() -> parser.parse(file("extrato.csv", broken)))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("Linha 16")
                .hasMessageContaining("Data mov.");
    }

    @Test
    void parse_rejectsInvalidAmountWithLineNumber() throws IOException {
        String broken = syntheticCsv().replace(";-58,71;", ";abc;");

        assertThatThrownBy(() -> parser.parse(file("extrato.csv", broken)))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("Linha 16")
                .hasMessageContaining("Montante");
    }
}
