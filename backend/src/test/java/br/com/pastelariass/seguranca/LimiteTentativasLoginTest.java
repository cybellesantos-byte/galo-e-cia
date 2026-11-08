package br.com.pastelariass.seguranca;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Teste UNITÁRIO do limite. Usa um relógio que AVANÇAMOS à mão, para testar
 * a janela de 15 minutos sem esperar 15 minutos de verdade.
 */
class LimiteTentativasLoginTest {

    /** Relógio de teste: a hora só muda quando o teste manda. */
    private static final class RelogioManual extends Clock {
        private Instant agora;

        RelogioManual(Instant inicio) { this.agora = inicio; }

        void avancar(Duration tempo) { agora = agora.plus(tempo); }

        @Override public Instant instant() { return agora; }
        @Override public ZoneId getZone() { return ZoneId.of("UTC"); }
        @Override public Clock withZone(ZoneId zone) { return this; }
    }

    private final RelogioManual relogio = new RelogioManual(Instant.parse("2026-10-27T12:00:00Z"));
    private final LimiteTentativasLogin limite = new LimiteTentativasLogin(relogio);

    private void falhar(String username, int vezes) {
        for (int i = 0; i < vezes; i++) {
            limite.registrarFalha(username);
        }
    }

    @Test
    void quatroFalhasNaoBloqueiaCincoBloqueia() {
        falhar("ana", LimiteTentativasLogin.MAXIMO_FALHAS - 1);
        assertThat(limite.bloqueado("ana")).isFalse();

        falhar("ana", 1);
        assertThat(limite.bloqueado("ana")).isTrue();
    }

    @Test
    void bloqueioExpiraDepoisDaJanela() {
        falhar("ana", LimiteTentativasLogin.MAXIMO_FALHAS);
        assertThat(limite.bloqueado("ana")).isTrue();

        relogio.avancar(LimiteTentativasLogin.JANELA.plusSeconds(1));

        assertThat(limite.bloqueado("ana")).isFalse();
    }

    @Test
    void falhasEspacadasPelaJanelaNaoSomam() {
        falhar("ana", 4);
        relogio.avancar(LimiteTentativasLogin.JANELA.plusSeconds(1)); // a contagem antiga expira
        falhar("ana", 4);

        assertThat(limite.bloqueado("ana")).isFalse();
    }

    @Test
    void loginCertoZeraAContagem() {
        falhar("ana", 4);
        limite.limpar("ana");
        falhar("ana", 4);

        assertThat(limite.bloqueado("ana")).isFalse();
    }

    @Test
    void nomeEhIgnoradoEntreMaiusculasEEspacos() {
        falhar("Ana", LimiteTentativasLogin.MAXIMO_FALHAS);

        assertThat(limite.bloqueado("  ANA ")).isTrue();
    }

    @Test
    void bloqueioDeUmNomeNaoAfetaOutro() {
        falhar("ana", LimiteTentativasLogin.MAXIMO_FALHAS);

        assertThat(limite.bloqueado("bruno")).isFalse();
    }
}
