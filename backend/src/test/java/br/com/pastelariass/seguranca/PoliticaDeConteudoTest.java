package br.com.pastelariass.seguranca;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A "lista de fornecedores permitidos" do navegador (CSP) só funciona se as páginas
 * obedecerem a ela. Este teste olha os arquivos da tela (HTML e JavaScript) e acusa o que o
 * navegador bloquearia: script solto no HTML, onclick="..." e style="..." escritos no texto.
 *
 * Sem este teste, alguém acrescentaria um style="..." num dia qualquer, e o elemento
 * ficaria sem estilo só no navegador, sem nenhum erro nos outros testes.
 */
class PoliticaDeConteudoTest {

    private static final Path TELA = Path.of("src/main/resources/static");

    private static final Pattern ESTILO_NO_TEXTO = Pattern.compile("\\sstyle\\s*=\\s*[\"']");
    private static final Pattern EVENTO_NO_TEXTO = Pattern.compile("\\son(click|load|error|change|input|submit|focus|blur|keydown|keyup|mouse\\w+)\\s*=\\s*[\"']");
    private static final Pattern SCRIPT_SOLTO = Pattern.compile("<script(?![^>]*\\ssrc=)[^>]*>");
    private static final Pattern ESTILO_EM_BLOCO = Pattern.compile("<style[\\s>]");

    private static List<Path> arquivos(String extensao) throws IOException {
        try (Stream<Path> todos = Files.walk(TELA)) {
            return todos
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(extensao))
                    // O Bootstrap é de terceiros e vem inteiro do próprio sistema: não é código nosso.
                    .filter(p -> !p.toString().replace('\\', '/').contains("/vendor/"))
                    .toList();
        }
    }

    /** Linhas de código: ignora comentários, que podem citar style="..." só para explicar. */
    private static boolean ehComentario(String linha) {
        var t = linha.trim();
        return t.startsWith("*") || t.startsWith("//") || t.startsWith("/*") || t.startsWith("<!--") || t.startsWith("-->");
    }

    private static List<String> achados(List<Path> lista, Pattern padrao) throws IOException {
        var resultado = new ArrayList<String>();
        for (var arquivo : lista) {
            var linhas = Files.readAllLines(arquivo);
            for (int i = 0; i < linhas.size(); i++) {
                if (!ehComentario(linhas.get(i)) && padrao.matcher(linhas.get(i)).find()) {
                    resultado.add(TELA.relativize(arquivo).toString().replace('\\', '/') + ":" + (i + 1));
                }
            }
        }
        return resultado;
    }

    @Test
    void asTelasExistemParaOTesteTerOQueOlhar() throws IOException {
        // Se a pasta mudar de lugar, os testes abaixo passariam "vazios". Este impede isso.
        assertThat(arquivos(".js")).hasSizeGreaterThan(10);
        assertThat(arquivos(".html")).hasSizeGreaterThanOrEqualTo(2);
    }

    @Test
    void nenhumJavaScriptEscreveStyleNoTexto() throws IOException {
        assertThat(achados(arquivos(".js"), ESTILO_NO_TEXTO))
                .as("style=\"...\" escrito no JavaScript (o CSP bloqueia; use data-... e element.style)")
                .isEmpty();
    }

    @Test
    void nenhumHtmlTemStyleScriptSoltoNemEventoNoTexto() throws IOException {
        var html = arquivos(".html");
        assertThat(achados(html, ESTILO_NO_TEXTO)).as("style=\"...\" no HTML").isEmpty();
        assertThat(achados(html, EVENTO_NO_TEXTO)).as("onclick=\"...\" e parecidos no HTML").isEmpty();
        assertThat(achados(html, SCRIPT_SOLTO)).as("<script> sem src no HTML").isEmpty();
        assertThat(achados(html, ESTILO_EM_BLOCO)).as("<style> no HTML").isEmpty();
    }

    @Test
    void nenhumJavaScriptEscreveEventoNoTexto() throws IOException {
        assertThat(achados(arquivos(".js"), EVENTO_NO_TEXTO))
                .as("onclick=\"...\" e parecidos montados no JavaScript (use addEventListener)")
                .isEmpty();
    }
}
