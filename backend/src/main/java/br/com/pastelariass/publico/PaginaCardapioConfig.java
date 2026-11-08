package br.com.pastelariass.publico;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * O endereço bonito da página do cliente: /cardapio mostra o arquivo cardapio.html.
 * É o link que vai para o Instagram e para o WhatsApp.
 *
 * É só uma página (HTML, CSS e JavaScript, sem dado nenhum). Os dados vêm da
 * porta pública /api/publico/cardapio, que tem as suas próprias trancas.
 */
@Configuration
public class PaginaCardapioConfig implements WebMvcConfigurer {

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/cardapio").setViewName("forward:/cardapio.html");
    }
}
