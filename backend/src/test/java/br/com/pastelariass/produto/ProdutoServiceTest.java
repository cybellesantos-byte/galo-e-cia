package br.com.pastelariass.produto;

import br.com.pastelariass.comum.Negocio;
import br.com.pastelariass.comum.RecursoNaoEncontradoException;
import br.com.pastelariass.comum.RegraDeNegocioException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Testes UNITÁRIOS do ProdutoService: sem Spring e sem banco.
 */
@ExtendWith(MockitoExtension.class)
class ProdutoServiceTest {

    @Mock ProdutoRepository produtoRepository;
    @Mock br.com.pastelariass.auditoria.AuditoriaService auditoria;

    private ProdutoService service() {
        return new ProdutoService(produtoRepository, auditoria);
    }

    private static CadastroProdutoRequest cadastro(Negocio negocio, UnidadeVenda unidade, String preco) {
        return new CadastroProdutoRequest(negocio, "Tilápia", "Peixe", unidade,
                new BigDecimal(preco), null);
    }

    // ── Cadastro: caminho feliz ──────────────────────────────────────────────

    @Test
    void cadastraTilapiaPorKgComPrecoExato() {
        when(produtoRepository.saveAndFlush(any(Produto.class))).thenAnswer(chamada -> chamada.getArgument(0));

        var produto = service().cadastrar(cadastro(Negocio.PEIXARIA, UnidadeVenda.KG, "28.00"));

        assertThat(produto.getNegocio()).isEqualTo(Negocio.PEIXARIA);
        assertThat(produto.getUnidadeVenda()).isEqualTo(UnidadeVenda.KG);
        assertThat(produto.getPreco()).isEqualByComparingTo("28.00");
        assertThat(produto.getCustoEstimado()).isNull();
        assertThat(produto.isAtivo()).isTrue();
        assertThat(produto.isDisponivel()).isTrue();
    }

    @Test
    void custoEstimadoEhOpcionalMasQuandoInformadoSeGuarda() {
        when(produtoRepository.saveAndFlush(any(Produto.class))).thenAnswer(chamada -> chamada.getArgument(0));

        var produto = service().cadastrar(new CadastroProdutoRequest(Negocio.PASTELARIA, "Pastel de carne",
                "Pastel", UnidadeVenda.UNIDADE, new BigDecimal("8.00"), new BigDecimal("3.50")));

        assertThat(produto.getCustoEstimado()).isEqualByComparingTo("3.50");
    }

    // ── Descrição (cardápio do cliente) ──────────────────────────────────────

    @Test
    void descricaoEhGuardadaSemEspacosDasPontas() {
        when(produtoRepository.saveAndFlush(any(Produto.class))).thenAnswer(chamada -> chamada.getArgument(0));

        var produto = service().cadastrar(new CadastroProdutoRequest(Negocio.PASTELARIA, "Pastel de frango",
                "Simples", UnidadeVenda.UNIDADE, new BigDecimal("8.00"), null, "  frango desfiado  "));

        assertThat(produto.getDescricao()).isEqualTo("frango desfiado");
    }

    @Test
    void descricaoMuitoLongaEhRecusadaNoServicoMesmoSemPassarPelaAPI() {
        var longa = "a".repeat(ProdutoService.TAMANHO_MAXIMO_DESCRICAO + 1);

        assertThatThrownBy(() -> service().cadastrar(new CadastroProdutoRequest(Negocio.PASTELARIA, "Pastel de frango",
                "Simples", UnidadeVenda.UNIDADE, new BigDecimal("8.00"), null, longa)))
                .isInstanceOf(RegraDeNegocioException.class)
                .hasMessage("A descrição deve ter no máximo 200 caracteres.");
        verify(produtoRepository, never()).saveAndFlush(any());
    }

    // ── Negócio × unidade ────────────────────────────────────────────────────

    @Test
    void peixariaNaoVendePorUnidade() {
        assertThatThrownBy(() -> service().cadastrar(cadastro(Negocio.PEIXARIA, UnidadeVenda.UNIDADE, "28.00")))
                .isInstanceOf(RegraDeNegocioException.class)
                .hasMessage("A peixaria vende por kg.");
        verify(produtoRepository, never()).saveAndFlush(any());
    }

    @Test
    void pastelariaNaoVendePorKg() {
        assertThatThrownBy(() -> service().cadastrar(cadastro(Negocio.PASTELARIA, UnidadeVenda.KG, "8.00")))
                .isInstanceOf(RegraDeNegocioException.class)
                .hasMessage("A pastelaria vende por unidade.");
    }

    // ── Preço e custo ────────────────────────────────────────────────────────

    @Test
    void precoZeroOuNegativoNaoEhAceito() {
        assertThatThrownBy(() -> service().cadastrar(cadastro(Negocio.PEIXARIA, UnidadeVenda.KG, "0.00")))
                .isInstanceOf(RegraDeNegocioException.class)
                .hasMessage("O preço deve ser maior que zero.");
        assertThatThrownBy(() -> service().cadastrar(cadastro(Negocio.PEIXARIA, UnidadeVenda.KG, "-5.00")))
                .isInstanceOf(RegraDeNegocioException.class)
                .hasMessage("O preço não pode ser negativo.");
    }

    @Test
    void precoComTresCasasDecimaisEhRecusadoEmVezDeArredondado() {
        assertThatThrownBy(() -> service().cadastrar(cadastro(Negocio.PEIXARIA, UnidadeVenda.KG, "28.005")))
                .isInstanceOf(RegraDeNegocioException.class)
                .hasMessage("O preço aceita no máximo 2 casas decimais.");
    }

    @Test
    void custoNegativoNaoEhAceito() {
        assertThatThrownBy(() -> service().cadastrar(new CadastroProdutoRequest(Negocio.PASTELARIA,
                "Pastel de carne", "Pastel", UnidadeVenda.UNIDADE, new BigDecimal("8.00"), new BigDecimal("-1"))))
                .isInstanceOf(RegraDeNegocioException.class)
                .hasMessage("O custo estimado não pode ser negativo.");
    }

    // ── Nome repetido ────────────────────────────────────────────────────────

    @Test
    void nomeRepetidoNoMesmoNegocioEhRecusado() {
        when(produtoRepository.existsByNegocioAndNomeIgnoreCase(Negocio.PEIXARIA, "Tilápia")).thenReturn(true);

        assertThatThrownBy(() -> service().cadastrar(cadastro(Negocio.PEIXARIA, UnidadeVenda.KG, "28.00")))
                .isInstanceOf(RegraDeNegocioException.class)
                .hasMessage("Já existe um produto com esse nome neste negócio.");
        verify(produtoRepository, never()).saveAndFlush(any());
    }

    @Test
    void corridaPeloNomeEntreConferenciaEGravacaoTambemViraMensagemClara() {
        when(produtoRepository.saveAndFlush(any(Produto.class))).thenThrow(new DataIntegrityViolationException("índice"));

        assertThatThrownBy(() -> service().cadastrar(cadastro(Negocio.PEIXARIA, UnidadeVenda.KG, "28.00")))
                .isInstanceOf(RegraDeNegocioException.class)
                .hasMessage("Já existe um produto com esse nome neste negócio.");
    }

    // ── Alteração ────────────────────────────────────────────────────────────

    @Test
    void alteracaoNaoContaOProprioProdutoComoNomeRepetido() {
        var existente = new Produto(Negocio.PEIXARIA, "Tilápia", "Peixe", UnidadeVenda.KG,
                new BigDecimal("28.00"), null);
        when(produtoRepository.findById(7L)).thenReturn(Optional.of(existente));
        when(produtoRepository.existsByNegocioAndNomeIgnoreCaseAndIdNot(Negocio.PEIXARIA, "Tilápia", 7L))
                .thenReturn(false);
        when(produtoRepository.saveAndFlush(any(Produto.class))).thenAnswer(chamada -> chamada.getArgument(0));

        var alterado = service().alterar(7L, new AlteracaoProdutoRequest("Tilápia", "Peixe",
                new BigDecimal("30.00"), null));

        assertThat(alterado.getPreco()).isEqualByComparingTo("30.00");
    }

    @Test
    void alterarProdutoInexistenteDaNaoEncontrado() {
        when(produtoRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().alterar(99L, new AlteracaoProdutoRequest("X", "Y",
                new BigDecimal("1.00"), null)))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    // ── Esgotar e desativar: nunca apagam ────────────────────────────────────

    @Test
    void esgotarMarcaComoIndisponivelSemApagarNada() {
        var pastel = new Produto(Negocio.PASTELARIA, "Pastel de carne", "Pastel", UnidadeVenda.UNIDADE,
                new BigDecimal("8.00"), null);
        when(produtoRepository.findById(1L)).thenReturn(Optional.of(pastel));

        var resultado = service().definirDisponivel(1L, false);

        assertThat(resultado.isDisponivel()).isFalse();
        assertThat(resultado.isAtivo()).isTrue();
        verify(produtoRepository, never()).delete(any());
        verify(produtoRepository, never()).deleteById(any());
    }

    @Test
    void desativarTiraDaListaDeVendaSemApagarNada() {
        var pastel = new Produto(Negocio.PASTELARIA, "Pastel de carne", "Pastel", UnidadeVenda.UNIDADE,
                new BigDecimal("8.00"), null);
        when(produtoRepository.findById(1L)).thenReturn(Optional.of(pastel));

        var resultado = service().definirAtivo(1L, false);

        assertThat(resultado.isAtivo()).isFalse();
        verify(produtoRepository, never()).delete(any());
        verify(produtoRepository, never()).deleteById(any());
    }
}
