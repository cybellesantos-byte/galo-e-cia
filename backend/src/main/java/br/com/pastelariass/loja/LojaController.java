package br.com.pastelariass.loja;

import br.com.pastelariass.comum.Negocio;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/**
 * A loja vista por quem trabalha (exige login). O cliente não passa por aqui:
 * ele usa a porta pública, que só lê.
 *
 *   GET /api/loja/{negocio}            → como está agora        (qualquer pessoa logada)
 *   PUT /api/loja/{negocio}/aberta     → abre ou fecha          (equipe e administrador)
 *   PUT /api/loja/{negocio}/whatsapp   → número dos pedidos     (SÓ o administrador)
 *
 * O número do WhatsApp é só do administrador porque é para onde vão TODOS os
 * pedidos: quem consegue trocá-lo consegue desviar os pedidos para outro telefone.
 */
@RestController
@RequestMapping("/api/loja")
public class LojaController {

    private final LojaService lojaService;

    public LojaController(LojaService lojaService) {
        this.lojaService = lojaService;
    }

    @GetMapping("/{negocio}")
    public LojaResponse consultar(@PathVariable Negocio negocio) {
        return LojaResponse.de(lojaService.buscar(negocio));
    }

    @PutMapping("/{negocio}/aberta")
    public LojaResponse abrirOuFechar(@PathVariable Negocio negocio, @Valid @RequestBody AbertaRequest dados) {
        return LojaResponse.de(lojaService.definirAberta(negocio, dados.aberta()));
    }

    @PutMapping("/{negocio}/whatsapp")
    @PreAuthorize("hasRole('ADMIN')")
    public LojaResponse definirWhatsapp(@PathVariable Negocio negocio, @Valid @RequestBody WhatsappRequest dados) {
        return LojaResponse.de(lojaService.definirWhatsapp(negocio, dados.whatsapp()));
    }

    /** O JSON: {"aberta": true} */
    public record AbertaRequest(@NotNull Boolean aberta) {
    }

    /** O JSON: {"whatsapp": "(83) 99999-8888"}. Em branco apaga o número. */
    public record WhatsappRequest(@Size(max = 30) String whatsapp) {
    }

    public record LojaResponse(Negocio negocio, boolean aberta, String whatsapp, Instant alteradoEm, String alteradoPor) {

        static LojaResponse de(Loja loja) {
            return new LojaResponse(loja.getNegocio(), loja.isAberta(), loja.getWhatsapp(),
                    loja.getAlteradoEm(), loja.getAlteradoPor());
        }
    }
}
