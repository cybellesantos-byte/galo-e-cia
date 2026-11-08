package br.com.pastelariass.loja;

import br.com.pastelariass.comum.RegraDeNegocioException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A limpeza do número de WhatsApp, sem banco: é só uma conta com texto.
 * O número vira link (wa.me/...), por isso cada caso estranho é recusado.
 */
class LojaServiceTest {

    @Test
    void numeroFormatadoViraSoDigitosComOCodigoDoPais() {
        assertThat(LojaService.normalizarWhatsapp("(83) 99999-8888")).isEqualTo("5583999998888");
        assertThat(LojaService.normalizarWhatsapp("83 99999 8888")).isEqualTo("5583999998888");
        assertThat(LojaService.normalizarWhatsapp("+55 (83) 99999-8888")).isEqualTo("5583999998888");
        assertThat(LojaService.normalizarWhatsapp("  83999998888  ")).isEqualTo("5583999998888");
    }

    @Test
    void telefoneFixoComDezDigitosTambemEhAceito() {
        assertThat(LojaService.normalizarWhatsapp("(83) 3333-4444")).isEqualTo("558333334444");
    }

    @Test
    void emBrancoApagaONumero() {
        assertThat(LojaService.normalizarWhatsapp(null)).isNull();
        assertThat(LojaService.normalizarWhatsapp("")).isNull();
        assertThat(LojaService.normalizarWhatsapp("   ")).isNull();
    }

    @Test
    void letrasESimbolosSaoRecusados() {
        assertThatThrownBy(() -> LojaService.normalizarWhatsapp("83 99999-888a"))
                .isInstanceOf(RegraDeNegocioException.class);
        // Tentativa de desviar o link para outro lugar:
        assertThatThrownBy(() -> LojaService.normalizarWhatsapp("83999998888/../evil"))
                .isInstanceOf(RegraDeNegocioException.class);
        assertThatThrownBy(() -> LojaService.normalizarWhatsapp("<script>"))
                .isInstanceOf(RegraDeNegocioException.class);
    }

    @Test
    void tamanhoErradoEhRecusado() {
        // Falta o DDD (9 dígitos).
        assertThatThrownBy(() -> LojaService.normalizarWhatsapp("99999-8888"))
                .isInstanceOf(RegraDeNegocioException.class);
        // Dígitos demais.
        assertThatThrownBy(() -> LojaService.normalizarWhatsapp("5583999998888123"))
                .isInstanceOf(RegraDeNegocioException.class);
        // Outro país (44), 13 dígitos.
        assertThatThrownBy(() -> LojaService.normalizarWhatsapp("44 83999998888"))
                .isInstanceOf(RegraDeNegocioException.class);
    }
}
