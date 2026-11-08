package br.com.pastelariass.publico;

import br.com.pastelariass.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * O robô com "bilhetinho falso": quando o pedido NÃO vem de um porteiro de confiança,
 * o sistema tem de ignorar o X-Forwarded-For. Senão o robô troca de nome a cada
 * pedido e nunca é barrado.
 *
 * Para simular "quem bateu na porta não é o porteiro", declaramos como porteiro de
 * confiança um endereço qualquer (10.9.9.9). A conexão do teste vem de 127.0.0.1,
 * então ela passa a ser uma pessoa comum da rua.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@TestPropertySource(properties = {
        "app.publico.limite-por-minuto=3",
        "server.tomcat.remoteip.internal-proxies=10\\.9\\.9\\.9"
})
class EnderecoDoClienteSemProxyConfiavelApiTest {

    @LocalServerPort int porta;

    private final HttpClient http = HttpClient.newHttpClient();

    private int pedirComoCliente(String enderecoFalso) throws Exception {
        var pedido = HttpRequest.newBuilder(URI.create("http://localhost:" + porta + "/api/publico/cardapio"))
                .header("X-Forwarded-For", enderecoFalso)
                .GET()
                .build();
        return http.send(pedido, HttpResponse.BodyHandlers.discarding()).statusCode();
    }

    @Test
    void roboQueEscreveUmEnderecoFalsoACadaPedidoContinuaSendoBarrado() throws Exception {
        for (int i = 1; i <= 3; i++) {
            assertThat(pedirComoCliente("198.51.100." + i)).isEqualTo(200);
        }
        // Mesmo com um "nome" novo no bilhetinho, o quarto pedido é barrado:
        // o sistema ignorou o bilhetinho e contou pelo endereço real de quem bateu.
        assertThat(pedirComoCliente("198.51.100.4")).isEqualTo(429);
    }
}
