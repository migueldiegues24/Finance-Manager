package com.miguel.financemanager.service.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DescriptionNormalizerTest {

    @Test
    void normalize_uppercasesAndRemovesAccents() {
        assertThat(DescriptionNormalizer.normalize("Pagamento Serviços Água café"))
                .isEqualTo("PAGAMENTO SERVICOS AGUA CAFE");
    }

    @Test
    void normalize_collapsesAndTrimsWhitespaceIncludingNbsp() {
        assertThat(DescriptionNormalizer.normalize("  COMPRAS   C.DEB \tCAFE  "))
                .isEqualTo("COMPRAS C.DEB CAFE");
    }

    @Test
    void normalize_keepsBankPrefixes() {
        assertThat(DescriptionNormalizer.normalize("Trf Mbway João")).isEqualTo("TRF MBWAY JOAO");
        assertThat(DescriptionNormalizer.normalize("TFI Empresa")).isEqualTo("TFI EMPRESA");
        assertThat(DescriptionNormalizer.normalize("TRF P2P Maria")).isEqualTo("TRF P2P MARIA");
    }

    @Test
    void normalize_returnsEmptyForNull() {
        assertThat(DescriptionNormalizer.normalize(null)).isEmpty();
    }
}
