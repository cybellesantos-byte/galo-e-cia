package br.com.pastelariass.publico;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Teste UNITÁRIO do limite de acessos ao cardápio público. Usa um relógio que
 * AVANÇAMOS à mão, para testar a janela de 1 minuto sem esperar 1 minuto.
 */
class LimiteRequisicoesPublicasTest {

    /** Relógio de teste: a hora só muda quando o teste manda. */
    private static final class RelogioManual extends Clock {
        private Instant agora;

        RelogioManual(Instant inicio) { this.agora = inicio; }

        void avancar(Duration tempo) { agora = agora.plus(tempo); }

        @Override public Instant instant() { return agora; }
        @Override public ZoneId getZone() { return ZoneId.of("UTC"); }
        @Override public Clock withZone(ZoneId zone) { return this; }
    }

    private static final int LIMITE = 5;

    private final RelogioManual relogio = new RelogioManual(Instant.parse("2026-10-30T12:00:00Z"));
    private final LimiteRequisicoesPublicas limite = new LimiteRequisicoesPublicas(relogio, LIMITE);

    /** Faz N acessos seguidos e devolve a resposta do último. */
    private long acessar(String endereco, int vezes) {
        long ultimo = 0;
        for (int i = 0; i < vezes; i++) {
            ultimo = limite.registrar(endereco);
        }
        return ultimo;
    }

    @Test
    void ateOLimiteNaoTrava() {
        assertThat(acessar("10.0.0.1", LIMITE)).isZero();
    }

    @Test
    void umAcessoAlemDoLimiteEhBarradoEDizQuantoEsperar() {
        acessar("10.0.0.1", LIMITE);

        var espera = limite.registrar("10.0.0.1");

        assertThat(espera).isBetween(1L, 60L);
    }

    @Test
    void aEsperaDiminuiConformeOMinutoPassa() {
        acessar("10.0.0.1", LIMITE);
        relogio.avancar(Duration.ofSeconds(45));

        assertThat(limite.registrar("10.0.0.1")).isEqualTo(15);   // faltam 15 s dos 60
    }

    @Test
    void depoisDoMinutoAContagemRecomeca() {
        acessar("10.0.0.1", LIMITE + 3);
        relogio.avancar(Duration.ofSeconds(61));

        assertThat(limite.registrar("10.0.0.1")).isZero();
    }

    @Test
    void cadaEnderecoTemAContaDele() {
        acessar("10.0.0.1", LIMITE + 3);   // este está barrado...

        assertThat(limite.registrar("10.0.0.2")).isZero();   // ...e este não tem nada a ver com isso
    }

    @Test
    void robosComMuitosEnderecosNaoEncheAMemoriaParaSempre() {
        // Enche a tabela até o teto com endereços diferentes, todos "dentro do minuto".
        for (int i = 0; i < LimiteRequisicoesPublicas.MAXIMO_DE_ENDERECOS; i++) {
            limite.registrar("robo-" + i);
        }
        // Tabela lotada: um endereço novo passa (sem ser guardado), em vez de derrubar todo mundo.
        assertThat(limite.registrar("cliente-novo")).isZero();

        // Passado o minuto, os antigos expiram e a tabela se limpa quando chega alguém novo.
        relogio.avancar(Duration.ofSeconds(61));
        assertThat(limite.registrar("cliente-novo")).isZero();
        assertThat(acessar("cliente-novo", LIMITE)).isPositive();   // agora ele é guardado e contado
    }
}
