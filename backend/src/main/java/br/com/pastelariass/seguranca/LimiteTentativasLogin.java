package br.com.pastelariass.seguranca;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Freia quem tenta adivinhar a senha de alguém.
 *
 * Regra: 5 senhas erradas para o mesmo nome de usuário dentro de 15 minutos
 * bloqueiam novas tentativas para esse nome por 15 minutos, mesmo com a senha
 * certa. Passado o prazo, a contagem recomeça do zero.
 *
 * Analogia: o porteiro que, depois de cinco crachás recusados para o mesmo
 * nome, não deixa mais ninguém tentar aquele nome por um tempo.
 *
 * Limitações conhecidas (anotadas para o futuro):
 *  - a contagem fica na memória: reiniciar o sistema zera os bloqueios;
 *  - quem troca o NOME a cada tentativa não é barrado por esta regra
 *    (um limite por endereço de origem viria depois, com cuidado: a pastelaria
 *    inteira pode sair do mesmo endereço e não pode ser travada por um erro de um).
 */
@Component
public class LimiteTentativasLogin {

    static final int MAXIMO_FALHAS = 5;
    static final Duration JANELA = Duration.ofMinutes(15);

    private final Map<String, Tentativas> tentativas = new ConcurrentHashMap<>();
    private final Clock relogio;

    /** Quantas falhas houve e quando começou a contagem atual. */
    private record Tentativas(int falhas, Instant inicio) {}

    public LimiteTentativasLogin(Clock relogio) {
        this.relogio = relogio;
    }

    /** true se este nome está bloqueado agora. */
    public boolean bloqueado(String username) {
        var chave = chave(username);
        var atual = tentativas.get(chave);
        if (atual == null) {
            return false;
        }
        if (expirou(atual)) {
            tentativas.remove(chave);
            return false;
        }
        return atual.falhas() >= MAXIMO_FALHAS;
    }

    public void registrarFalha(String username) {
        tentativas.merge(chave(username), new Tentativas(1, agora()), (antigo, novo) ->
                expirou(antigo) ? novo : new Tentativas(antigo.falhas() + 1, antigo.inicio()));
    }

    /** Login certo: a contagem desse nome volta a zero. */
    public void limpar(String username) {
        tentativas.remove(chave(username));
    }

    private boolean expirou(Tentativas t) {
        return agora().isAfter(t.inicio().plus(JANELA));
    }

    private Instant agora() {
        return Instant.now(relogio).truncatedTo(ChronoUnit.MICROS);
    }

    /** "Ana" e "ana" contam como o mesmo nome, como no login. */
    private static String chave(String username) {
        return username.strip().toLowerCase(Locale.ROOT);
    }
}
