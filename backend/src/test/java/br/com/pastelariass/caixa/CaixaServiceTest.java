package br.com.pastelariass.caixa;

import br.com.pastelariass.comum.Negocio;
import br.com.pastelariass.comum.RegraDeNegocioException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Testes UNITÁRIOS do CaixaService: sem Spring e sem banco.
 */
@ExtendWith(MockitoExtension.class)
class CaixaServiceTest {

    private static final Instant MEIO_DIA = Instant.parse("2026-10-27T15:00:00Z");

    @Mock CaixaRepository caixaRepository;
    @Mock MovimentacaoCaixaRepository movimentacaoRepository;
    @Mock br.com.pastelariass.venda.VendaRepository vendaRepository;
    @Mock br.com.pastelariass.despesa.DespesaRepository despesaRepository;
    @Mock br.com.pastelariass.repasse.RepasseRepository repasseRepository;
    @Mock br.com.pastelariass.auditoria.AuditoriaService auditoria;

    private CaixaService service() {
        return new CaixaService(caixaRepository, movimentacaoRepository, vendaRepository, despesaRepository,
                repasseRepository, auditoria, Clock.fixed(MEIO_DIA, ZoneOffset.UTC));
    }

    private static Caixa caixaAberto(BigDecimal saldoInicial) {
        return new Caixa(Negocio.PASTELARIA, saldoInicial, MEIO_DIA, "ana");
    }

    private static MovimentacaoCaixa movimento(TipoMovimentacao tipo, String valor) {
        return new MovimentacaoCaixa(1L, tipo, new BigDecimal(valor), MEIO_DIA, "ana", null);
    }

    // ── Abertura ─────────────────────────────────────────────────────────────

    @Test
    void abreCaixaComSaldoInicial() {
        when(caixaRepository.findByNegocioAndFechadoEmIsNull(Negocio.PASTELARIA)).thenReturn(Optional.empty());
        when(caixaRepository.saveAndFlush(any(Caixa.class))).thenAnswer(chamada -> chamada.getArgument(0));

        var caixa = service().abrir(Negocio.PASTELARIA, new BigDecimal("100.00"));

        assertThat(caixa.estaAberto()).isTrue();
        assertThat(caixa.getSaldoInicial()).isEqualByComparingTo("100.00");
    }

    @Test
    void naoAbreSegundoCaixaDoMesmoNegocio() {
        when(caixaRepository.findByNegocioAndFechadoEmIsNull(Negocio.PASTELARIA))
                .thenReturn(Optional.of(caixaAberto(new BigDecimal("50"))));

        assertThatThrownBy(() -> service().abrir(Negocio.PASTELARIA, new BigDecimal("100")))
                .isInstanceOf(RegraDeNegocioException.class)
                .hasMessage("Já existe um caixa aberto para este negócio.");
        verify(caixaRepository, never()).saveAndFlush(any());
    }

    @Test
    void duasAberturasAoMesmoTempoTambemViramMensagemClara() {
        when(caixaRepository.findByNegocioAndFechadoEmIsNull(Negocio.PEIXARIA)).thenReturn(Optional.empty());
        when(caixaRepository.saveAndFlush(any(Caixa.class))).thenThrow(new DataIntegrityViolationException("índice"));

        assertThatThrownBy(() -> service().abrir(Negocio.PEIXARIA, new BigDecimal("100")))
                .isInstanceOf(RegraDeNegocioException.class)
                .hasMessage("Já existe um caixa aberto para este negócio.");
    }

    @Test
    void saldoInicialNegativoNaoEhAceito() {
        assertThatThrownBy(() -> service().abrir(Negocio.PASTELARIA, new BigDecimal("-1")))
                .isInstanceOf(RegraDeNegocioException.class)
                .hasMessage("O saldo inicial não pode ser negativo.");
    }

    // ── Pronto quando: abre com 100, sangria de 30, conta 68 → esperado 70, diferença −2 ──

    @Test
    void fechamentoCalculaEsperadoEDiferencaComoNoPlano() {
        var caixa = caixaAberto(new BigDecimal("100.00"));
        when(caixaRepository.buscarParaAlterar(1L)).thenReturn(Optional.of(caixa));
        when(movimentacaoRepository.findByCaixaIdOrderByQuandoAsc(any()))
                .thenReturn(List.of(movimento(TipoMovimentacao.SANGRIA, "30.00")));

        var fechado = service().fechar(1L, new BigDecimal("68.00"), "faltou R$ 2");

        assertThat(fechado.estaAberto()).isFalse();
        assertThat(fechado.getEsperado()).isEqualByComparingTo("70.00");
        assertThat(fechado.getValorContado()).isEqualByComparingTo("68.00");
        assertThat(fechado.getDiferenca()).isEqualByComparingTo("-2.00");
        assertThat(fechado.getObservacaoFechamento()).isEqualTo("faltou R$ 2");
    }

    @Test
    void reforcoSobeOEsperadoDoCaixaAberto() {
        var caixa = caixaAberto(new BigDecimal("50.00"));
        when(movimentacaoRepository.findByCaixaIdOrderByQuandoAsc(any()))
                .thenReturn(List.of(movimento(TipoMovimentacao.REFORCO, "20.00")));

        assertThat(service().esperadoAtual(caixa)).isEqualByComparingTo("70.00");
    }

    @Test
    void reforcoGravaAMovimentacaoEDevolveOCaixa() {
        var caixa = caixaAberto(new BigDecimal("50.00"));
        when(caixaRepository.buscarParaAlterar(1L)).thenReturn(Optional.of(caixa));
        when(movimentacaoRepository.saveAndFlush(any(MovimentacaoCaixa.class))).thenAnswer(chamada -> chamada.getArgument(0));

        var resultado = service().reforco(1L, new BigDecimal("20.00"), "troco");

        assertThat(resultado).isSameAs(caixa);
        verify(movimentacaoRepository).saveAndFlush(argThat(m ->
                m.getTipo() == TipoMovimentacao.REFORCO && m.getValor().compareTo(new BigDecimal("20.00")) == 0));
    }

    // ── Sangria ──────────────────────────────────────────────────────────────

    @Test
    void sangriaNaoPodePassarDoQueHaNaGaveta() {
        when(caixaRepository.buscarParaAlterar(1L)).thenReturn(Optional.of(caixaAberto(new BigDecimal("100"))));
        when(movimentacaoRepository.findByCaixaIdOrderByQuandoAsc(any())).thenReturn(List.of());

        assertThatThrownBy(() -> service().sangria(1L, new BigDecimal("150"), null))
                .isInstanceOf(RegraDeNegocioException.class)
                .hasMessage("Não há dinheiro suficiente na gaveta para essa sangria.");
        verify(movimentacaoRepository, never()).saveAndFlush(any());
    }

    @Test
    void sangriaDeZeroNaoEhAceita() {
        when(caixaRepository.buscarParaAlterar(1L)).thenReturn(Optional.of(caixaAberto(new BigDecimal("100"))));

        assertThatThrownBy(() -> service().sangria(1L, BigDecimal.ZERO, null))
                .isInstanceOf(RegraDeNegocioException.class)
                .hasMessage("O valor deve ser maior que zero.");
    }

    // ── Caixa fechado ────────────────────────────────────────────────────────

    @Test
    void caixaFechadoNaoAceitaSangriaNemReforco() {
        var fechado = caixaAberto(new BigDecimal("100"));
        fechado.fechar("ana", MEIO_DIA, new BigDecimal("100"), new BigDecimal("100"), null);
        when(caixaRepository.buscarParaAlterar(1L)).thenReturn(Optional.of(fechado));

        assertThatThrownBy(() -> service().reforco(1L, new BigDecimal("10"), null))
                .isInstanceOf(RegraDeNegocioException.class)
                .hasMessage("Este caixa já está fechado e não pode mais ser alterado.");
        verify(movimentacaoRepository, never()).saveAndFlush(any());
    }

    // ── Composição do esperado (a conta aberta da tela de fechamento) ────────

    /**
     * 100 + 50 (reforço) − 30 (sangria) + 140 (vendas em dinheiro: 80 + 60)
     * − 20 (despesa) − 10 (repasse) = 230. Cada parcela separada, e o total.
     */
    @Test
    void composicaoSeparaCadaParcelaESomaOEsperado() {
        var caixa = caixaAberto(new BigDecimal("100.00"));
        when(movimentacaoRepository.findByCaixaIdOrderByQuandoAsc(any())).thenReturn(List.of(
                movimento(TipoMovimentacao.REFORCO, "50.00"),
                movimento(TipoMovimentacao.SANGRIA, "30.00")));
        // As vendas são criadas ANTES do when(...): criar um dublê (com o seu próprio
        // when) no meio de outro when confunde o Mockito ("unfinished stubbing").
        var vendas = List.of(venda("80.00"), venda("60.00"));
        when(vendaRepository.findByCaixaIdAndFormaPagamentoAndCanceladaFalse(any(),
                eq(br.com.pastelariass.venda.FormaPagamento.DINHEIRO)))
                .thenReturn(vendas);
        var despesa = mock(br.com.pastelariass.despesa.Despesa.class);
        when(despesa.getValor()).thenReturn(new BigDecimal("20.00"));
        when(despesaRepository.findByCaixaId(any())).thenReturn(List.of(despesa));
        var repasse = mock(br.com.pastelariass.repasse.RepasseEntrega.class);
        when(repasse.getValor()).thenReturn(new BigDecimal("10.00"));
        when(repasseRepository.findByCaixaId(any())).thenReturn(List.of(repasse));

        var c = service().composicao(caixa);

        assertThat(c.saldoInicial()).isEqualByComparingTo("100.00");
        assertThat(c.reforcos()).isEqualByComparingTo("50.00");
        assertThat(c.sangrias()).isEqualByComparingTo("30.00");
        assertThat(c.vendasDinheiro()).isEqualByComparingTo("140.00");
        assertThat(c.despesas()).isEqualByComparingTo("20.00");
        assertThat(c.repasses()).isEqualByComparingTo("10.00");
        assertThat(c.esperado()).isEqualByComparingTo("230.00");
        // A composição e o esperado "oficial" usam a mesma conta: nunca podem divergir.
        assertThat(service().esperadoAtual(caixa)).isEqualByComparingTo(c.esperado());
    }

    @Test
    void caixaFechadoNaoTemComposicao() {
        var fechado = caixaAberto(new BigDecimal("100"));
        fechado.fechar("ana", MEIO_DIA, new BigDecimal("100"), new BigDecimal("100"), null);

        assertThat(service().composicao(fechado)).isNull();
    }

    private static br.com.pastelariass.venda.Venda venda(String total) {
        var venda = mock(br.com.pastelariass.venda.Venda.class);
        when(venda.getTotal()).thenReturn(new BigDecimal(total));
        return venda;
    }
}
