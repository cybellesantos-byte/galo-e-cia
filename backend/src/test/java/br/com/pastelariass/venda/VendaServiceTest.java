package br.com.pastelariass.venda;

import br.com.pastelariass.auditoria.AuditoriaService;
import br.com.pastelariass.caixa.Caixa;
import br.com.pastelariass.caixa.CaixaRepository;
import br.com.pastelariass.comum.Negocio;
import br.com.pastelariass.comum.RegraDeNegocioException;
import br.com.pastelariass.localidade.Localidade;
import br.com.pastelariass.localidade.LocalidadeRepository;
import br.com.pastelariass.produto.Produto;
import br.com.pastelariass.produto.ProdutoRepository;
import br.com.pastelariass.produto.UnidadeVenda;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Testes UNITÁRIOS do VendaService: sem Spring e sem banco.
 */
@ExtendWith(MockitoExtension.class)
class VendaServiceTest {

    private static final Instant MEIO_DIA = Instant.parse("2026-10-27T15:00:00Z");

    @Mock VendaRepository vendaRepository;
    @Mock VendaItemRepository itemRepository;
    @Mock CaixaRepository caixaRepository;
    @Mock ProdutoRepository produtoRepository;
    @Mock LocalidadeRepository localidadeRepository;
    @Mock AuditoriaService auditoria;
    @Mock br.com.pastelariass.repasse.RepasseService repasseService;

    private VendaService service() {
        return new VendaService(vendaRepository, itemRepository, caixaRepository, produtoRepository,
                localidadeRepository, auditoria, repasseService, Clock.fixed(MEIO_DIA, ZoneOffset.UTC));
    }

    private static Produto pastelDeCarne() {
        return new Produto(Negocio.PASTELARIA, "Pastel de carne", "Pastel", UnidadeVenda.UNIDADE,
                new BigDecimal("8.00"), null);
    }

    private static Produto tilapia() {
        return new Produto(Negocio.PEIXARIA, "Tilápia", "Peixe", UnidadeVenda.KG,
                new BigDecimal("28.00"), null);
    }

    /** Pedido da pastelaria, com quantidade inteira. */
    private static CriacaoVendaRequest pedido(TipoVenda tipo, Long localidadeId, FormaPagamento forma,
                                              String valorRecebido, int quantidade) {
        return new CriacaoVendaRequest(UUID.randomUUID(), Negocio.PASTELARIA, Origem.BALCAO, tipo, localidadeId,
                forma, valorRecebido == null ? null : new BigDecimal(valorRecebido), null,
                List.of(new ItemVendaRequest(1L, BigDecimal.valueOf(quantidade))));
    }

    /** Pedido da peixaria, com quantidade em kg. */
    private static CriacaoVendaRequest pedidoPeixaria(String quilos, String valorRecebido) {
        return new CriacaoVendaRequest(UUID.randomUUID(), Negocio.PEIXARIA, Origem.BALCAO, TipoVenda.RETIRADA, null,
                FormaPagamento.DINHEIRO, valorRecebido == null ? null : new BigDecimal(valorRecebido), null,
                List.of(new ItemVendaRequest(1L, new BigDecimal(quilos))));
    }

    /** Caixa aberto do negócio e chave ainda livre: o caminho feliz comum aos testes. */
    private void caixaAbertoEChaveLivre(Negocio negocio) {
        var caixa = new Caixa(negocio, new BigDecimal("100"), MEIO_DIA, "ana");
        when(vendaRepository.findByChaveDaVenda(any())).thenReturn(Optional.empty());
        when(caixaRepository.findByNegocioAndFechadoEmIsNull(negocio)).thenReturn(Optional.of(caixa));
        when(caixaRepository.buscarParaAlterar(any())).thenReturn(Optional.of(caixa));
    }

    private void caixaAbertoEChaveLivre() {
        caixaAbertoEChaveLivre(Negocio.PASTELARIA);
    }

    // ── Troco e pagamento ────────────────────────────────────────────────────

    /** Venda de R$ 32 paga com R$ 50: o troco é R$ 18, e a venda vale R$ 32. */
    @Test
    void trocoDeR18EmVendaDeR32PagaComR50() {
        caixaAbertoEChaveLivre();
        when(produtoRepository.findById(1L)).thenReturn(Optional.of(pastelDeCarne()));
        when(vendaRepository.saveAndFlush(any(Venda.class))).thenAnswer(chamada -> chamada.getArgument(0));

        var venda = service().registrar(pedido(TipoVenda.RETIRADA, null, FormaPagamento.DINHEIRO, "50.00", 4));

        assertThat(venda.getTotal()).isEqualByComparingTo("32.00");
        assertThat(venda.getValorRecebido()).isEqualByComparingTo("50.00");
        assertThat(venda.getTroco()).isEqualByComparingTo("18.00");
        assertThat(venda.getSituacaoPagamento()).isEqualTo(SituacaoPagamento.PAGO);
    }

    @Test
    void dinheiroInsuficienteEhRecusado() {
        caixaAbertoEChaveLivre();
        when(produtoRepository.findById(1L)).thenReturn(Optional.of(pastelDeCarne()));

        assertThatThrownBy(() -> service().registrar(pedido(TipoVenda.RETIRADA, null, FormaPagamento.DINHEIRO, "20.00", 4)))
                .isInstanceOf(RegraDeNegocioException.class)
                .hasMessage("O valor recebido é menor que o total da venda.");
        verify(vendaRepository, never()).saveAndFlush(any());
    }

    @Test
    void pixNaoInformaValorRecebidoENasceComoPendente() {
        caixaAbertoEChaveLivre();
        when(produtoRepository.findById(1L)).thenReturn(Optional.of(pastelDeCarne()));
        when(vendaRepository.saveAndFlush(any(Venda.class))).thenAnswer(chamada -> chamada.getArgument(0));

        var venda = service().registrar(pedido(TipoVenda.RETIRADA, null, FormaPagamento.PIX, null, 2));

        assertThat(venda.getTotal()).isEqualByComparingTo("16.00");
        assertThat(venda.getSituacaoPagamento()).isEqualTo(SituacaoPagamento.PENDENTE);
        assertThat(venda.getTroco()).isNull();
    }

    @Test
    void cartaoNaoInformaValorRecebidoENasceComoPendente() {
        caixaAbertoEChaveLivre();
        when(produtoRepository.findById(1L)).thenReturn(Optional.of(pastelDeCarne()));
        when(vendaRepository.saveAndFlush(any(Venda.class))).thenAnswer(chamada -> chamada.getArgument(0));

        var venda = service().registrar(pedido(TipoVenda.RETIRADA, null, FormaPagamento.CARTAO, null, 2));

        assertThat(venda.getTotal()).isEqualByComparingTo("16.00");
        assertThat(venda.getSituacaoPagamento()).isEqualTo(SituacaoPagamento.PENDENTE);
        assertThat(venda.getTroco()).isNull();
    }

    // ── Entrega ──────────────────────────────────────────────────────────────

    @Test
    void entregaCongelaATaxaDaLocalidadeNoTotal() {
        caixaAbertoEChaveLivre();
        when(produtoRepository.findById(1L)).thenReturn(Optional.of(pastelDeCarne()));
        when(localidadeRepository.findById(2L)).thenReturn(Optional.of(new Localidade("Saco", new BigDecimal("6.00"))));
        when(vendaRepository.saveAndFlush(any(Venda.class))).thenAnswer(chamada -> chamada.getArgument(0));

        var venda = service().registrar(pedido(TipoVenda.ENTREGA, 2L, FormaPagamento.PIX, null, 4));

        assertThat(venda.getSubtotal()).isEqualByComparingTo("32.00");
        assertThat(venda.getTaxaEntrega()).isEqualByComparingTo("6.00");
        assertThat(venda.getTotal()).isEqualByComparingTo("38.00");
    }

    /** Taxa informada substitui a padrão no que o cliente paga; a padrão fica registrada. */
    @Test
    void taxaInformadaSubstituiAPadraoEGeraRepasse() {
        caixaAbertoEChaveLivre();
        when(produtoRepository.findById(1L)).thenReturn(Optional.of(pastelDeCarne()));
        when(localidadeRepository.findById(2L)).thenReturn(Optional.of(new Localidade("Saco", new BigDecimal("6.00"))));
        when(vendaRepository.saveAndFlush(any(Venda.class))).thenAnswer(chamada -> chamada.getArgument(0));
        var pedido = new CriacaoVendaRequest(UUID.randomUUID(), Negocio.PASTELARIA, Origem.BALCAO, TipoVenda.ENTREGA,
                2L, FormaPagamento.PIX, null, null, List.of(new ItemVendaRequest(1L, BigDecimal.valueOf(4))),
                new BigDecimal("8.00"));

        var venda = service().registrar(pedido);

        assertThat(venda.getTaxaPadrao()).isEqualByComparingTo("6.00");
        assertThat(venda.getTaxaEntrega()).isEqualByComparingTo("8.00");
        assertThat(venda.getTotal()).isEqualByComparingTo("40.00");
        verify(repasseService).gerar(any(), eq(new BigDecimal("6.00")), eq(new BigDecimal("8.00")), any());
    }

    @Test
    void retiradaNaoAceitaTaxaInformada() {
        caixaAbertoEChaveLivre();
        when(produtoRepository.findById(1L)).thenReturn(Optional.of(pastelDeCarne()));
        var pedido = new CriacaoVendaRequest(UUID.randomUUID(), Negocio.PASTELARIA, Origem.BALCAO, TipoVenda.RETIRADA,
                null, FormaPagamento.PIX, null, null, List.of(new ItemVendaRequest(1L, BigDecimal.ONE)),
                new BigDecimal("5.00"));

        assertThatThrownBy(() -> service().registrar(pedido))
                .isInstanceOf(RegraDeNegocioException.class)
                .hasMessage("Venda para retirada não tem taxa de entrega.");
    }

    @Test
    void retiradaComLocalidadeEhRecusada() {
        caixaAbertoEChaveLivre();
        when(produtoRepository.findById(1L)).thenReturn(Optional.of(pastelDeCarne()));

        assertThatThrownBy(() -> service().registrar(pedido(TipoVenda.RETIRADA, 2L, FormaPagamento.PIX, null, 1)))
                .isInstanceOf(RegraDeNegocioException.class)
                .hasMessage("Venda para retirada não tem localidade de entrega.");
    }

    // ── Produto e quantidade ─────────────────────────────────────────────────

    @Test
    void produtoEsgotadoNaoPodeSerVendido() {
        caixaAbertoEChaveLivre();
        var esgotado = pastelDeCarne();
        esgotado.alterarDisponivel(false);
        when(produtoRepository.findById(1L)).thenReturn(Optional.of(esgotado));

        assertThatThrownBy(() -> service().registrar(pedido(TipoVenda.RETIRADA, null, FormaPagamento.PIX, null, 1)))
                .isInstanceOf(RegraDeNegocioException.class)
                .hasMessage("O produto Pastel de carne está esgotado.");
        verify(vendaRepository, never()).saveAndFlush(any());
    }

    /** Venda de peixe só entra no caixa da peixaria: produto de outro negócio é recusado. */
    @Test
    void produtoDaPeixariaNaoEntraNaVendaDaPastelaria() {
        caixaAbertoEChaveLivre();
        when(produtoRepository.findById(1L)).thenReturn(Optional.of(tilapia()));

        assertThatThrownBy(() -> service().registrar(pedido(TipoVenda.RETIRADA, null, FormaPagamento.PIX, null, 1)))
                .isInstanceOf(RegraDeNegocioException.class)
                .hasMessage("O produto Tilápia não é da pastelaria.");
    }

    @Test
    void mesmoProdutoDuasVezesNoPedidoEhRecusado() {
        caixaAbertoEChaveLivre();
        when(produtoRepository.findById(1L)).thenReturn(Optional.of(pastelDeCarne()));
        var pedidoRepetido = new CriacaoVendaRequest(UUID.randomUUID(), Negocio.PASTELARIA, Origem.BALCAO,
                TipoVenda.RETIRADA, null, FormaPagamento.PIX, null, null,
                List.of(new ItemVendaRequest(1L, BigDecimal.ONE), new ItemVendaRequest(1L, BigDecimal.TWO)));

        assertThatThrownBy(() -> service().registrar(pedidoRepetido))
                .isInstanceOf(RegraDeNegocioException.class)
                .hasMessage("Cada produto deve aparecer uma vez só na venda. Some as quantidades.");
    }

    @Test
    void quantidadeZeroNaoEhAceita() {
        caixaAbertoEChaveLivre();
        when(produtoRepository.findById(1L)).thenReturn(Optional.of(pastelDeCarne()));

        assertThatThrownBy(() -> service().registrar(pedido(TipoVenda.RETIRADA, null, FormaPagamento.PIX, null, 0)))
                .isInstanceOf(RegraDeNegocioException.class)
                .hasMessage("A quantidade deve ser maior que zero.");
    }

    // ── Peixaria: venda por peso ─────────────────────────────────────────────

    /** Pronto quando: 1,350 kg a R$ 28,00 = R$ 37,80. */
    @Test
    void tilapiaPorPesoSaiPorOPrecoVezesOPesoExato() {
        caixaAbertoEChaveLivre(Negocio.PEIXARIA);
        when(produtoRepository.findById(1L)).thenReturn(Optional.of(tilapia()));
        when(vendaRepository.saveAndFlush(any(Venda.class))).thenAnswer(chamada -> chamada.getArgument(0));

        var venda = service().registrar(pedidoPeixaria("1.350", "50.00"));

        assertThat(venda.getTotal()).isEqualByComparingTo("37.80");
        assertThat(venda.getTroco()).isEqualByComparingTo("12.20");
    }

    /** Arredondamento "metade para cima": 1,333 kg × R$ 28,00 = R$ 37,324 → R$ 37,32. */
    @Test
    void pesoArredondaOTotalDoItemParaCentavos() {
        caixaAbertoEChaveLivre(Negocio.PEIXARIA);
        when(produtoRepository.findById(1L)).thenReturn(Optional.of(tilapia()));
        when(vendaRepository.saveAndFlush(any(Venda.class))).thenAnswer(chamada -> chamada.getArgument(0));

        var venda = service().registrar(pedidoPeixaria("1.333", "40.00"));

        assertThat(venda.getTotal()).isEqualByComparingTo("37.32");
    }

    @Test
    void pesoComMaisDeTresCasasEhRecusado() {
        caixaAbertoEChaveLivre(Negocio.PEIXARIA);
        when(produtoRepository.findById(1L)).thenReturn(Optional.of(tilapia()));

        assertThatThrownBy(() -> service().registrar(pedidoPeixaria("1.3335", "40.00")))
                .isInstanceOf(RegraDeNegocioException.class)
                .hasMessage("A quantidade aceita no máximo 3 casas decimais.");
    }

    @Test
    void peixariaNaoFazEntrega() {
        caixaAbertoEChaveLivre(Negocio.PEIXARIA);
        when(produtoRepository.findById(1L)).thenReturn(Optional.of(tilapia()));
        var pedidoComEntrega = new CriacaoVendaRequest(UUID.randomUUID(), Negocio.PEIXARIA, Origem.WHATSAPP,
                TipoVenda.ENTREGA, 2L, FormaPagamento.PIX, null, null,
                List.of(new ItemVendaRequest(1L, new BigDecimal("1.000"))));

        assertThatThrownBy(() -> service().registrar(pedidoComEntrega))
                .isInstanceOf(RegraDeNegocioException.class)
                .hasMessage("A peixaria não tem entrega nesta versão.");
    }

    @Test
    void semCaixaDaPeixariaAbertoNaoHaVendaDePeixe() {
        when(vendaRepository.findByChaveDaVenda(any())).thenReturn(Optional.empty());
        when(caixaRepository.findByNegocioAndFechadoEmIsNull(Negocio.PEIXARIA)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().registrar(pedidoPeixaria("1.000", "50.00")))
                .isInstanceOf(RegraDeNegocioException.class)
                .hasMessage("Abra o caixa da peixaria antes de registrar vendas.");
    }

    // ── Caixa e duplicidade ──────────────────────────────────────────────────

    @Test
    void semCaixaDaPastelariaAbertoNaoHaVenda() {
        when(vendaRepository.findByChaveDaVenda(any())).thenReturn(Optional.empty());
        when(caixaRepository.findByNegocioAndFechadoEmIsNull(Negocio.PASTELARIA)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().registrar(pedido(TipoVenda.RETIRADA, null, FormaPagamento.PIX, null, 1)))
                .isInstanceOf(RegraDeNegocioException.class)
                .hasMessage("Abra o caixa da pastelaria antes de registrar vendas.");
    }

    /** Mesma chave de novo (duplo clique): devolve a venda que já existe, sem criar outra. */
    @Test
    void chaveRepetidaDevolveAVendaJaRegistrada() {
        var existente = new Venda(UUID.randomUUID(), 1L, Origem.BALCAO, TipoVenda.RETIRADA, null, null,
                BigDecimal.ZERO, new BigDecimal("8.00"), FormaPagamento.PIX, null, null, null, MEIO_DIA, "ana");
        var pedido = new CriacaoVendaRequest(UUID.randomUUID(), Negocio.PASTELARIA, Origem.BALCAO,
                TipoVenda.RETIRADA, null, FormaPagamento.PIX, null, null,
                List.of(new ItemVendaRequest(1L, BigDecimal.ONE)));
        when(vendaRepository.findByChaveDaVenda(pedido.chaveDaVenda())).thenReturn(Optional.of(existente));

        var resultado = service().registrar(pedido);

        assertThat(resultado).isSameAs(existente);
        verify(vendaRepository, never()).saveAndFlush(any());
        verify(caixaRepository, never()).buscarParaAlterar(any());
    }
}
