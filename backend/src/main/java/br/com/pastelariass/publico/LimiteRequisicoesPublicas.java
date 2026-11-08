package br.com.pastelariass.publico;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Freia quem acessa o cardápio público demais, de um mesmo endereço.
 *
 * Regra: cada endereço pode fazer N acessos por minuto (padrão 120). Passou
 * disso, recebe "tente de novo em instantes" até o minuto acabar.
 *
 * Analogia: a fila do balcão. Quem fica pedindo "me dá o cardápio" mil vezes por
 * minuto atrapalha quem está na fila de verdade; o atendente pede para esperar.
 *
 * O padrão de 120 deixa folga: a página pergunta de novo a cada poucos segundos
 * (cerca de 6 por minuto) e vários clientes podem sair do mesmo endereço (a
 * operadora de celular costuma juntar muita gente num endereço só).
 *
 * Limitações conhecidas (anotadas para a Etapa 11, do servidor):
 *  - a contagem fica na memória: reiniciar o sistema zera;
 *  - o "endereço" é o que chega ao sistema. Atrás de um proxy (o servidor de
 *    verdade), todos pareceriam o mesmo endereço do proxy, e o limite pegaria
 *    todo mundo junto. A Etapa 11 configura o sistema para confiar só no proxy
 *    e ler o endereço real do cliente;
 *  - contra um ataque de muitos endereços ao mesmo tempo, este limite não basta:
 *    quem segura isso é o proxy e o firewall.
 */
@Component
public class LimiteRequisicoesPublicas {

    static final Duration JANELA = Duration.ofMinutes(1);

    /**
     * Teto de endereços guardados na memória. Sem ele, um robô que muda de endereço a
     * cada pedido encheria a memória do sistema.
     */
    static final int MAXIMO_DE_ENDERECOS = 50_000;

    private final Map<String, Contagem> contagens = new ConcurrentHashMap<>();
    private final Clock relogio;
    private final int limitePorJanela;

    /** Quantos acessos houve e quando começou a contagem deste minuto. */
    private record Contagem(int acessos, Instant inicio) {}

    public LimiteRequisicoesPublicas(Clock relogio, @Value("${app.publico.limite-por-minuto:120}") int limitePorJanela) {
        this.relogio = relogio;
        this.limitePorJanela = limitePorJanela;
    }

    /**
     * Conta mais um acesso deste endereço.
     *
     * @return 0 se pode passar; senão, quantos segundos esperar até o minuto acabar.
     */
    public long registrar(String endereco) {
        var agora = agora();

        if (contagens.size() >= MAXIMO_DE_ENDERECOS && !contagens.containsKey(endereco)) {
            descartarExpirados(agora);
            if (contagens.size() >= MAXIMO_DE_ENDERECOS) {
                // Tabela cheia de endereços com minuto ainda valendo: não guarda este.
                // É um cardápio só de leitura e barato; negar a todos por causa da lotação
                // trancaria clientes de verdade. Quem segura esse ataque é o proxy.
                return 0;
            }
        }

        var contagem = contagens.compute(endereco, (chave, atual) ->
                atual == null || expirou(atual, agora)
                        ? new Contagem(1, agora)
                        : new Contagem(atual.acessos() + 1, atual.inicio()));

        if (contagem.acessos() <= limitePorJanela) {
            return 0;
        }
        var faltam = Duration.between(agora, contagem.inicio().plus(JANELA));
        return Math.max(1, (faltam.toMillis() + 999) / 1000);   // arredonda para cima
    }

    private void descartarExpirados(Instant agora) {
        contagens.values().removeIf(contagem -> expirou(contagem, agora));
    }

    private static boolean expirou(Contagem contagem, Instant agora) {
        return !agora.isBefore(contagem.inicio().plus(JANELA));
    }

    private Instant agora() {
        return Instant.now(relogio).truncatedTo(ChronoUnit.MICROS);
    }
}
