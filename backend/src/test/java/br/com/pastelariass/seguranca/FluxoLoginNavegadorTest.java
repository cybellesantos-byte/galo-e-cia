package br.com.pastelariass.seguranca;

import br.com.pastelariass.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Simula o navegador de ponta a ponta: o sistema sobe com um servidor HTTP
 * real numa porta aleatória, e um cliente HTTP que guarda cookies conversa
 * com ele, exatamente como o Chrome faria.
 *
 * Por que não usar MockMvc aqui? O atalho .with(csrf()) dos outros testes
 * troca, por dentro, o repositório de tokens do filtro de segurança. Aqui o
 * CSRF é 100% real: cookie XSRF-TOKEN de ida, header X-XSRF-TOKEN de volta.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class FluxoLoginNavegadorTest {

    @Value("${local.server.port}")
    int porta;

    CookieManager cookies;
    HttpClient navegador;

    @BeforeEach
    void abrirNavegadorLimpo() {
        cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        navegador = HttpClient.newBuilder().cookieHandler(cookies).build();
    }

    @Test
    void fluxoCompletoDeLoginELogout() throws Exception {
        // 1. A página pede o token CSRF ao abrir.
        var csrf = enviar(get("/api/auth/csrf"));
        assertThat(csrf.statusCode()).isEqualTo(204);
        assertThat(csrf.headers().firstValue("Set-Cookie").orElseThrow())
                .startsWith("XSRF-TOKEN=")
                .doesNotContainIgnoringCase("HttpOnly"); // este o JavaScript PRECISA ler

        // 2. Login devolvendo o token no header, como o frontend fará.
        var login = enviar(post("/api/auth/login", """
                {"username":"admin","senha":"senha-de-teste-123"}"""));
        assertThat(login.statusCode()).isEqualTo(200);
        assertThat(login.body()).contains("\"papel\":\"ADMIN\"").doesNotContain("senha");
        assertThat(login.headers().allValues("Set-Cookie"))
                .anySatisfy(c -> assertThat(c)
                        .startsWith("JSESSIONID=")
                        .containsIgnoringCase("HttpOnly")        // JavaScript NÃO lê
                        .containsIgnoringCase("SameSite=Lax"));

        // 3. A sessão vale para as próximas requisições.
        assertThat(enviar(get("/api/auth/me")).statusCode()).isEqualTo(200);

        // 4. Logout (também exige CSRF) encerra a sessão.
        assertThat(enviar(post("/api/auth/logout", "")).statusCode()).isEqualTo(204);
        assertThat(enviar(get("/api/auth/me")).statusCode()).isEqualTo(401);
    }

    @Test
    void tokenCsrfInventadoEhRecusadoMesmoComOCookieCerto() throws Exception {
        enviar(get("/api/auth/csrf"));

        var request = HttpRequest.newBuilder(uri("/api/auth/login"))
                .header("Content-Type", "application/json")
                .header("X-XSRF-TOKEN", "token-inventado")
                .POST(HttpRequest.BodyPublishers.ofString("""
                        {"username":"admin","senha":"senha-de-teste-123"}"""))
                .build();

        assertThat(enviar(request).statusCode()).isEqualTo(403);
    }

    // ── utilitários ──────────────────────────────────────────────────────────

    private HttpRequest get(String caminho) {
        return HttpRequest.newBuilder(uri(caminho)).GET().build();
    }

    /** POST com JSON e o header X-XSRF-TOKEN lido do cookie, igual ao frontend. */
    private HttpRequest post(String caminho, String json) {
        return HttpRequest.newBuilder(uri(caminho))
                .header("Content-Type", "application/json")
                .header("X-XSRF-TOKEN", valorDoCookieCsrf())
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
    }

    private String valorDoCookieCsrf() {
        return cookies.getCookieStore().getCookies().stream()
                .filter(c -> c.getName().equals("XSRF-TOKEN"))
                .map(HttpCookie::getValue)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Cookie XSRF-TOKEN não recebido"));
    }

    private HttpResponse<String> enviar(HttpRequest request) throws Exception {
        return navegador.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private URI uri(String caminho) {
        return URI.create("http://localhost:" + porta + caminho);
    }
}
