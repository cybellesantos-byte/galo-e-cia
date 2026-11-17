package br.com.pastelariass.despesa;

import br.com.pastelariass.auditoria.AuditoriaService;
import br.com.pastelariass.caixa.Caixa;
import br.com.pastelariass.caixa.CaixaRepository;
import br.com.pastelariass.comum.Negocio;
import br.com.pastelariass.comum.RegraDeNegocioException;
import br.com.pastelariass.venda.FormaPagamento;
import br.com.pastelariass.venda.SituacaoPagamento;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Testes UNITÁRIOS do DespesaService: sem Spring e sem banco.
 *
 * O relógio está em 27/10/2026 às 12:00 de Brasília, então "hoje" é 27/10.
 */
@ExtendWith(MockitoExtension.class)
class DespesaServiceTest {

    private static final Instant MEIO_DIA = Instant.parse("2026-10-27T15:00:00Z");
    private static final LocalDate DATA_PASSADA = LocalDate.of(2026, 10, 20);

    @Mock DespesaRepository despesaRepository;
    @Mock DespesaItemRepository itemRepository;
    @Mock CaixaRepository caixaRepository;
    @Mock AuditoriaService auditoria;

    private DespesaService service() {
        return new DespesaService(despesaRepository, itemRepository, caixaRepository, auditoria,
                Clock.fixed(MEIO_DIA, ZoneOffset.UTC));
    }

    private static CriacaoDespesaRequest despesa(Negocio negocio, CategoriaDespesa categoria, String valor,
                                                 LocalDate data, FormaPagamento forma, String peso) {
        return new CriacaoDespesaRequest(negocio, "Tilápia do fornecedor", categoria, valor == null ? null : new BigDecimal(valor),
                data, "Ana", forma, peso == null ? null : new BigDecimal(peso), null, null);
    }

    private static ItemDespesaRequest item(String quantidade, UnidadeMedida unidade, String preco) {
        return new ItemDespesaRequest("Farinha", new BigDecimal(quantidade), unidade, new BigDecimal(preco));
    }

    private static CriacaoDespesaRequest ingredientes(List<ItemDespesaRequest> itens, String valorInformado) {
        return new CriacaoDespesaRequest(Negocio.PASTELARIA, "Compra da semana", CategoriaDespesa.INGREDIENTES,
                valorInformado == null ? null : new BigDecimal(valorInformado), DATA_PASSADA, "Ana",
                FormaPagamento.PIX, null, null, itens);
    }

    // ── Itens detalhados de ingredientes ─────────────────────────────────────

    /** 2 × R$ 5,00 + 1,5 kg × R$ 20,00 = R$ 40,00. O valor sai da soma dos itens. */
    @Test
    void valorDaDespesaVemDaSomaDosItens() {
        when(despesaRepository.saveAndFlush(any(Despesa.class))).thenAnswer(chamada -> chamada.getArgument(0));

        var salva = service().registrar(ingredientes(List.of(
                item("2", UnidadeMedida.UNIDADE, "5.00"),
                item("1.5", UnidadeMedida.KG, "20.00")), null));

        assertThat(salva.getValor()).isEqualByComparingTo("40.00");
        verify(itemRepository, times(2)).save(any(DespesaItem.class));
    }

    @Test
    void valorInformadoQueNaoBateComOsItensEhRecusado() {
        assertThatThrownBy(() -> service().registrar(ingredientes(List.of(
                item("2", UnidadeMedida.UNIDADE, "5.00")), "12.00")))
                .isInstanceOf(RegraDeNegocioException.class)
                .hasMessage("O valor informado não bate com a soma dos itens (R$ 10.00).");
        verify(despesaRepository, never()).saveAndFlush(any());
    }

    @Test
    void itensSoSeInformamEmIngredientes() {
        var pedido = new CriacaoDespesaRequest(Negocio.PASTELARIA, "Gás", CategoriaDespesa.GAS_E_ENERGIA, null,
                DATA_PASSADA, "Ana", FormaPagamento.PIX, null, null, List.of(item("1", UnidadeMedida.UNIDADE, "90.00")));

        assertThatThrownBy(() -> service().registrar(pedido))
                .isInstanceOf(RegraDeNegocioException.class)
                .hasMessage("Itens detalhados só se informam em despesa de ingredientes.");
    }

    @Test
    void semItensEValorObrigatorio() {
        assertThatThrownBy(() -> service().registrar(despesa(Negocio.PASTELARIA, CategoriaDespesa.INGREDIENTES,
                null, DATA_PASSADA, FormaPagamento.PIX, null)))
                .isInstanceOf(RegraDeNegocioException.class)
                .hasMessage("O valor é obrigatório.");
    }

    private void caixaAberto(Negocio negocio) {
        var caixa = new Caixa(negocio, new BigDecimal("100"), MEIO_DIA, "ana");
        when(caixaRepository.findByNegocioAndFechadoEmIsNull(negocio)).thenReturn(Optional.of(caixa));
        when(caixaRepository.buscarParaAlterar(any())).thenReturn(Optional.of(caixa));
    }

    // ── Paga ou pendente ─────────────────────────────────────────────────────

    @Test
    void despesaPagaEmDinheiroUsaOCaixaAbertoDoNegocio() {
        caixaAberto(Negocio.PEIXARIA);
        when(despesaRepository.saveAndFlush(any(Despesa.class))).thenAnswer(chamada -> chamada.getArgument(0));

        var salva = service().registrar(despesa(Negocio.PEIXARIA, CategoriaDespesa.COMPRA_DE_PEIXE,
                "500.00", DATA_PASSADA, FormaPagamento.DINHEIRO, "30.000"));

        assertThat(salva.getSituacao()).isEqualTo(SituacaoPagamento.PAGO);
        assertThat(salva.getPagoEm()).isEqualTo(MEIO_DIA);
        verify(caixaRepository).buscarParaAlterar(any());
    }

    /** Pix e cartão não mexem na gaveta: nem procura caixa. */
    @Test
    void despesaPagaPorPixNaoProcuraCaixa() {
        when(despesaRepository.saveAndFlush(any(Despesa.class))).thenAnswer(chamada -> chamada.getArgument(0));

        var salva = service().registrar(despesa(Negocio.PASTELARIA, CategoriaDespesa.INGREDIENTES,
                "30.00", DATA_PASSADA, FormaPagamento.PIX, null));

        assertThat(salva.getFormaPagamento()).isEqualTo(FormaPagamento.PIX);
        assertThat(salva.getCaixaId()).isNull();
        verify(caixaRepository, never()).findByNegocioAndFechadoEmIsNull(any());
    }

    @Test
    void despesaSemFormaDePagamentoFicaPendente() {
        when(despesaRepository.saveAndFlush(any(Despesa.class))).thenAnswer(chamada -> chamada.getArgument(0));

        var salva = service().registrar(despesa(Negocio.PASTELARIA, CategoriaDespesa.OUTROS,
                "20.00", DATA_PASSADA, null, null));

        assertThat(salva.getSituacao()).isEqualTo(SituacaoPagamento.PENDENTE);
        assertThat(salva.getPagoEm()).isNull();
    }

    @Test
    void dinheiroSemCaixaAbertoDoNegocioEhRecusado() {
        when(caixaRepository.findByNegocioAndFechadoEmIsNull(Negocio.PEIXARIA)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().registrar(despesa(Negocio.PEIXARIA, CategoriaDespesa.COMPRA_DE_PEIXE,
                "500.00", DATA_PASSADA, FormaPagamento.DINHEIRO, "30")))
                .isInstanceOf(RegraDeNegocioException.class)
                .hasMessage("Abra o caixa da peixaria antes de pagar em dinheiro.");
        verify(despesaRepository, never()).saveAndFlush(any());
    }

    @Test
    void pagarDespesaPendenteEmPixNaoMexeNoCaixa() {
        var pendente = new Despesa(Negocio.PASTELARIA, "Gás", CategoriaDespesa.GAS_E_ENERGIA, new BigDecimal("90"),
                DATA_PASSADA, "Ana", null, null, null, null, MEIO_DIA, "ana");
        when(despesaRepository.buscarParaAlterar(7L)).thenReturn(Optional.of(pendente));

        var paga = service().pagar(7L, new PagamentoDespesaRequest(FormaPagamento.PIX));

        assertThat(paga.estaPaga()).isTrue();
        assertThat(paga.getCaixaId()).isNull();
        verify(caixaRepository, never()).findByNegocioAndFechadoEmIsNull(any());
    }

    @Test
    void despesaJaPagaNaoEhPagaDeNovo() {
        var paga = new Despesa(Negocio.PASTELARIA, "Gás", CategoriaDespesa.GAS_E_ENERGIA, new BigDecimal("90"),
                DATA_PASSADA, "Ana", FormaPagamento.PIX, null, null, null, MEIO_DIA, "ana");
        when(despesaRepository.buscarParaAlterar(7L)).thenReturn(Optional.of(paga));

        assertThatThrownBy(() -> service().pagar(7L, new PagamentoDespesaRequest(FormaPagamento.DINHEIRO)))
                .isInstanceOf(RegraDeNegocioException.class)
                .hasMessage("Esta despesa já foi paga.");
    }

    // ── Categoria, peso e data ───────────────────────────────────────────────

    @Test
    void compraDePeixeSoExisteNaPeixaria() {
        assertThatThrownBy(() -> service().registrar(despesa(Negocio.PASTELARIA, CategoriaDespesa.COMPRA_DE_PEIXE,
                "500.00", DATA_PASSADA, FormaPagamento.PIX, "30")))
                .isInstanceOf(RegraDeNegocioException.class)
                .hasMessage("Compra de peixe é uma despesa da peixaria.");
    }

    @Test
    void pesoSoSeInformaEmCompraDePeixe() {
        assertThatThrownBy(() -> service().registrar(despesa(Negocio.PASTELARIA, CategoriaDespesa.INGREDIENTES,
                "40.00", DATA_PASSADA, FormaPagamento.PIX, "2")))
                .isInstanceOf(RegraDeNegocioException.class)
                .hasMessage("O peso só se informa em compra de peixe.");
    }

    @Test
    void dataNoFuturoEhRecusada() {
        assertThatThrownBy(() -> service().registrar(despesa(Negocio.PASTELARIA, CategoriaDespesa.OUTROS,
                "10.00", LocalDate.of(2026, 10, 28), null, null)))
                .isInstanceOf(RegraDeNegocioException.class)
                .hasMessage("A data da despesa não pode estar no futuro.");
    }

    // ── Resumo do mês ───────────────────────────────────────────────────────

    /**
     * Compra de R$ 500 com 30 kg: o preço médio é R$ 16,67 por kg. A despesa
     * pendente de R$ 100 entra no gasto do mês, mas não no preço por kg.
     */
    @Test
    void resumoSomaOGastoEOCalculaOPrecoMedioSoComAsComprasComPeso() {
        var compra = new Despesa(Negocio.PEIXARIA, "Tilápia", CategoriaDespesa.COMPRA_DE_PEIXE,
                new BigDecimal("500.00"), DATA_PASSADA, "Ana", FormaPagamento.PIX, null,
                new BigDecimal("30.000"), null, MEIO_DIA, "ana");
        var gelo = new Despesa(Negocio.PEIXARIA, "Gelo", CategoriaDespesa.OUTROS,
                new BigDecimal("100.00"), DATA_PASSADA, "Ana", null, null, null, null, MEIO_DIA, "ana");
        when(despesaRepository.findByNegocioAndDataDespesaBetweenOrderByDataDespesaDesc(
                eq(Negocio.PEIXARIA), any(), any())).thenReturn(List.of(compra, gelo));

        var resumo = service().resumo(Negocio.PEIXARIA, YearMonth.of(2026, 10));

        assertThat(resumo.totalGasto()).isEqualByComparingTo("600.00");
        assertThat(resumo.totalPago()).isEqualByComparingTo("500.00");
        assertThat(resumo.totalPendente()).isEqualByComparingTo("100.00");
        assertThat(resumo.kgComprados()).isEqualByComparingTo("30.000");
        assertThat(resumo.precoMedioPorKg()).isEqualByComparingTo("16.67");
    }
}
