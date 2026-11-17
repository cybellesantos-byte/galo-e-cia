package br.com.pastelariass.publico;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * A única porta pública do sistema: o cardápio, para quem não fez login.
 *
 *   GET /api/publico/cardapio
 *
 * É SÓ LEITURA, de propósito: nenhum POST, PUT ou DELETE neste pacote. A
 * liberação no SecurityConfig vale para este endereço exato, e uma rota nova
 * aqui continua trancada até alguém liberá-la de propósito.
 */
@RestController
@RequestMapping("/api/publico")
public class CardapioPublicoController {

    private final CardapioPublicoService cardapioService;

    public CardapioPublicoController(CardapioPublicoService cardapioService) {
        this.cardapioService = cardapioService;
    }

    @GetMapping("/cardapio")
    public ResponseEntity<CardapioPublico> cardapio() {
        // "Esgotado" tem que aparecer na hora. Sem isto, o navegador (ou um intermediário)
        // poderia guardar a resposta e mostrar um cardápio velho.
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(cardapioService.montar());
    }
}
