package br.com.pastelariass;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * Teste de fumaça: o sistema inteiro sobe contra um PostgreSQL real?
 *
 * Se algum @Entity não bater com a tabela, ou alguma migração falhar, este
 * teste quebra. Ele é o alarme mais barato que temos.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class BackendApplicationTests {

    @Test
    void contextoSobeComBancoEMigracoesAplicados() {
        // Vazio de propósito: a falha, se houver, acontece na inicialização do contexto.
    }
}
