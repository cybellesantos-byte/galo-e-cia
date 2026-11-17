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
 * O "porteiro" (proxy) na frente do sistema: o fiscal da fila tem de contar cada
 * cliente pelo endereço verdadeiro dele, e não pelo endereço do porteiro.
 *
 * Aqui o sistema sobe de verdade, numa porta real (o porteiro de mentira é este
 * teste, que escreve o "bilhetinho" X-Forwarded-For com o endereço do cliente).
 * Como a conexão vem do próprio computador (127.0.0.1), ela conta como um porteiro
 * de confiança, igual ao porteiro da rede interna do Render.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@TestPropertySource(properties = "app.publico.limite-por-minuto=3")
class EnderecoDoClienteAtrasDoProxyApiTest {

    @LocalServerPort int porta;

    private final HttpClient http = HttpClient.newHttpClient();

    /** Faz um pedido ao cardápio como se o porteiro estivesse repassando este cliente. */
    private int pedirComoCliente(String enderecoDoCliente) throws Exception {
        var pedido = HttpRequest.newBuilder(URI.create("http://localhost:" + porta + "/api/publico/cardapio"))
                .header("X-Forwarded-For", enderecoDoCliente)
                .GET()
                .build();
        return http.send(pedido, HttpResponse.BodyHandlers.discarding()).statusCode();
    }

    @Test
    void clientesDiferentesAtrasDoMesmoPorteiroTemCotasSeparadas() throws Exception {
        for (int i = 0; i < 3; i++) {
            assertThat(pedirComoCliente("203.0.113.50")).isEqualTo(200);
        }
        // O quarto pedido do primeiro cliente é barrado...
        assertThat(pedirComoCliente("203.0.113.50")).isEqualTo(429);

        // ...mas outro cliente, que passa pelo MESMO porteiro, ainda tem a cota inteira.
        assertThat(pedirComoCliente("203.0.113.51")).isEqualTo(200);
    }
}
