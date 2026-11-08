package br.com.pastelariass.produto;

import br.com.pastelariass.auditoria.AcaoAuditoria;
import br.com.pastelariass.auditoria.AuditoriaService;
import br.com.pastelariass.comum.Negocio;
import br.com.pastelariass.comum.RecursoNaoEncontradoException;
import br.com.pastelariass.comum.RegraDeNegocioException;
import br.com.pastelariass.comum.Validacoes;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * Regras do cadastro de produtos.
 *
 * Produto nunca é apagado. Ele sai da lista por "desativar" ou fica
 * "indisponível" quando acaba. Assim, as vendas antigas continuam válidas.
 */
@Service
public class ProdutoService {

    static final int TAMANHO_MAXIMO_NOME = 120;
    static final int TAMANHO_MAXIMO_CATEGORIA = 60;
    static final int TAMANHO_MAXIMO_DESCRICAO = 200;

    private static final String NOME_EM_USO = "Já existe um produto com esse nome neste negócio.";

    private final ProdutoRepository produtoRepository;
    private final AuditoriaService auditoria;

    public ProdutoService(ProdutoRepository produtoRepository, AuditoriaService auditoria) {
        this.produtoRepository = produtoRepository;
        this.auditoria = auditoria;
    }

    /**
     * Cadastra um produto. Recebe o objeto de pedido inteiro, para que cada
     * valor tenha nome e não possa ser passado na ordem errada.
     */
    @Transactional
    public Produto cadastrar(CadastroProdutoRequest dados) {
        var nome = Validacoes.texto(dados.nome(), TAMANHO_MAXIMO_NOME, "O nome");
        var categoria = Validacoes.texto(dados.categoria(), TAMANHO_MAXIMO_CATEGORIA, "A categoria");
        validarNegocioEUnidade(dados.negocio(), dados.unidadeVenda());
        var preco = validarPreco(dados.preco());
        var custo = validarCusto(dados.custoEstimado());
        var descricao = Validacoes.textoOpcional(dados.descricao(), TAMANHO_MAXIMO_DESCRICAO, "A descrição");
        conferirNomeLivre(dados.negocio(), nome, null);

        var produto = new Produto(dados.negocio(), nome, categoria, dados.unidadeVenda(), preco, custo);
        produto.alterarDescricao(descricao);
        return gravar(produto);
    }

    @Transactional
    public Produto alterar(Long id, AlteracaoProdutoRequest dados) {
        var produto = buscarPorId(id);
        var nome = Validacoes.texto(dados.nome(), TAMANHO_MAXIMO_NOME, "O nome");
        var categoria = Validacoes.texto(dados.categoria(), TAMANHO_MAXIMO_CATEGORIA, "A categoria");
        var preco = validarPreco(dados.preco());
        var custo = validarCusto(dados.custoEstimado());
        var descricao = Validacoes.textoOpcional(dados.descricao(), TAMANHO_MAXIMO_DESCRICAO, "A descrição");
        conferirNomeLivre(produto.getNegocio(), nome, id);

        var precoAntes = produto.getPreco();
        var custoAntes = produto.getCustoEstimado();
        produto.alterarDados(nome, categoria, preco, custo);
        produto.alterarDescricao(descricao);
        var salvo = gravar(produto);
        // Preço e custo mexem no faturamento e na margem: cada mudança fica registrada.
        auditoria.registrar(auditoria.quemEstaLogado(), AcaoAuditoria.PRODUTO_ALTERADO, nome,
                "preco=" + precoAntes + "->" + preco + ", custo=" + custoAntes + "->" + custo);
        return salvo;
    }

    /** Marca como esgotado (false) ou de volta à venda (true). Não mexe no cadastro. */
    @Transactional
    public Produto definirDisponivel(Long id, boolean disponivel) {
        var produto = buscarPorId(id);
        produto.alterarDisponivel(disponivel);
        return produto;
    }

    /** Tira da lista de venda (false) ou devolve à lista (true). */
    @Transactional
    public Produto definirAtivo(Long id, boolean ativo) {
        var produto = buscarPorId(id);
        produto.alterarAtivo(ativo);
        return produto;
    }

    /** Todos os produtos, ou só os de um negócio, em ordem de nome. */
    @Transactional(readOnly = true)
    public List<Produto> listar(Negocio negocio) {
        var ordem = Sort.by("nome");
        return negocio == null
                ? produtoRepository.findAll(ordem)
                : produtoRepository.findByNegocio(negocio, ordem);
    }

    @Transactional(readOnly = true)
    public Produto buscarPorId(Long id) {
        return produtoRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Produto não encontrado."));
    }

    /**
     * Grava agora. Se duas pessoas salvarem o mesmo nome ao mesmo tempo, as duas
     * passam pela conferência acima, mas só uma passa pelo índice único do banco.
     */
    private Produto gravar(Produto produto) {
        try {
            return produtoRepository.saveAndFlush(produto);
        } catch (DataIntegrityViolationException e) {
            throw new RegraDeNegocioException(NOME_EM_USO);
        }
    }

    /**
     * A pastelaria vende por unidade e a peixaria vende por kg. Misturar não
     * faz sentido: o total de um pastel não se calcula por peso.
     */
    private static void validarNegocioEUnidade(Negocio negocio, UnidadeVenda unidade) {
        if (negocio == null) {
            throw new RegraDeNegocioException("Informe o negócio: PASTELARIA ou PEIXARIA.");
        }
        if (unidade == null) {
            throw new RegraDeNegocioException("Informe a unidade de venda: UNIDADE ou KG.");
        }
        var esperada = negocio == Negocio.PEIXARIA ? UnidadeVenda.KG : UnidadeVenda.UNIDADE;
        if (unidade != esperada) {
            throw new RegraDeNegocioException(negocio == Negocio.PEIXARIA
                    ? "A peixaria vende por kg."
                    : "A pastelaria vende por unidade.");
        }
    }

    private static BigDecimal validarPreco(BigDecimal preco) {
        var valor = Validacoes.valorMonetario(preco, "O preço");
        if (valor.signum() == 0) {
            throw new RegraDeNegocioException("O preço deve ser maior que zero.");
        }
        return valor;
    }

    /** O custo é opcional: sem custo, fica null (não zero). */
    private static BigDecimal validarCusto(BigDecimal custo) {
        return custo == null ? null : Validacoes.valorMonetario(custo, "O custo estimado");
    }

    /** Na alteração, passe o id do próprio produto para ele não contar como repetido. */
    private void conferirNomeLivre(Negocio negocio, String nome, Long idDoProduto) {
        boolean emUso = idDoProduto == null
                ? produtoRepository.existsByNegocioAndNomeIgnoreCase(negocio, nome)
                : produtoRepository.existsByNegocioAndNomeIgnoreCaseAndIdNot(negocio, nome, idDoProduto);
        if (emUso) {
            throw new RegraDeNegocioException(NOME_EM_USO);
        }
    }
}
