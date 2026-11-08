package br.com.pastelariass.publico;

import br.com.pastelariass.comum.Negocio;
import br.com.pastelariass.localidade.Localidade;
import br.com.pastelariass.localidade.LocalidadeService;
import br.com.pastelariass.loja.LojaService;
import br.com.pastelariass.produto.Produto;
import br.com.pastelariass.produto.ProdutoRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Monta o cardápio que o cliente vê. Só LÊ: nada aqui grava no banco.
 *
 * O negócio é fixo (pastelaria), sem nenhum parâmetro vindo de fora. Quem não
 * escolhe nada não pode escolher errado, e ninguém consegue pedir a peixaria
 * ou outro negócio mexendo no endereço.
 */
@Service
public class CardapioPublicoService {

    static final Negocio NEGOCIO = Negocio.PASTELARIA;

    private final ProdutoRepository produtoRepository;
    private final LocalidadeService localidadeService;
    private final LojaService lojaService;

    public CardapioPublicoService(ProdutoRepository produtoRepository, LocalidadeService localidadeService,
                                  LojaService lojaService) {
        this.produtoRepository = produtoRepository;
        this.localidadeService = localidadeService;
        this.lojaService = lojaService;
    }

    @Transactional(readOnly = true)
    public CardapioPublico montar() {
        var loja = lojaService.buscar(NEGOCIO);

        var itens = produtoRepository.findByNegocio(NEGOCIO, Sort.by("nome")).stream()
                // "Fora da lista" não aparece. "Esgotado" aparece, marcado como esgotado.
                .filter(Produto::isAtivo)
                .map(p -> new CardapioPublico.Item(p.getNome(), p.getDescricao(), p.getCategoria(),
                        p.getPreco(), !p.isDisponivel()))
                .toList();

        var locais = localidadeService.listar().stream()
                .filter(Localidade::isAtivo)
                .map(l -> new CardapioPublico.LocalDeEntrega(l.getNome(), l.getTaxa()))
                .toList();

        return new CardapioPublico(loja.isAberta(), loja.getWhatsapp(), itens, locais);
    }
}
