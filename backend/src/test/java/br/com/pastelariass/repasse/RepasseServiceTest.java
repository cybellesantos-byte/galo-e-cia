package br.com.pastelariass.repasse;

import br.com.pastelariass.auditoria.AuditoriaService;
import br.com.pastelariass.caixa.Caixa;
import br.com.pastelariass.caixa.CaixaRepository;
import br.com.pastelariass.comum.Negocio;
import br.com.pastelariass.comum.RegraDeNegocioException;
import br.com.pastelariass.venda.FormaPagamento;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Testes UNITÁRIOS do RepasseService: sem Spring e sem banco.
 */
@ExtendWith(MockitoExtension.class)
class RepasseServiceTest {

    private static final Instant MEIO_DIA = Instant.parse("2026-10-27T15:00:00Z");
    private static final LocalDate HOJE = LocalDate.of(2026, 10, 27);

    @Mock RepasseRepository repasseRepository;
    @Mock CaixaRepository caixaRepository;
    @Mock br.com.pastelariass.venda.VendaRepository vendaRepository;
    @Mock br.com.pastelariass.localidade.LocalidadeRepository localidadeRepository;
    @Mock AuditoriaService auditoria;

    private RepasseService service() {
        return new RepasseService(repasseRepository, caixaRepository, vendaRepository, localidadeRepository,
                auditoria, Clock.fixed(MEIO_DIA, ZoneOffset.UTC));
    }

    private static RepasseEntrega pendente() {
        return pendente("6.00");
    }

    private static RepasseEntrega pendente(String valor) {
        return new RepasseEntrega(1L, new BigDecimal(valor), HOJE, MEIO_DIA);
    }

    // ── Valor do repasse ─────────────────────────────────────────────────────

    /** Cobrou mais que a padrão: o entregador recebe a cobrada, e o extra é dele. */
    @Test
    void repasseSobeComAMaiorTaxaCobrada() {
        when(repasseRepository.saveAndFlush(any(RepasseEntrega.class))).thenAnswer(chamada -> chamada.getArgument(0));

        var repasse = service().gerar(1L, new BigDecimal("6.00"), new BigDecimal("8.00"), HOJE);

        assertThat(repasse.getValor()).isEqualByComparingTo("8.00");
        assertThat(repasse.getSituacao()).isEqualTo(SituacaoRepasse.PENDENTE);
    }

    /** Cobrou igual ou menos que a padrão: o entregador recebe o combinado, a padrão. */
    @Test
    void repasseNaoCaiAbaixoDaPadrao() {
        when(repasseRepository.saveAndFlush(any(RepasseEntrega.class))).thenAnswer(chamada -> chamada.getArgument(0));

        var repasse = service().gerar(1L, new BigDecimal("6.00"), new BigDecimal("4.00"), HOJE);

        assertThat(repasse.getValor()).isEqualByComparingTo("6.00");
    }

    // ── Pagamento ────────────────────────────────────────────────────────────

    @Test
    void pagarEmDinheiroSemCaixaAbertoEhRecusado() {
        when(repasseRepository.buscarParaAlterar(1L)).thenReturn(Optional.of(pendente()));
        when(caixaRepository.findByNegocioAndFechadoEmIsNull(Negocio.PASTELARIA)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().pagar(1L, FormaPagamento.DINHEIRO))
                .isInstanceOf(RegraDeNegocioException.class)
                .hasMessage("Abra o caixa da pastelaria antes de pagar em dinheiro.");
    }

    @Test
    void pagarPixNaoPrecisaDeCaixa() {
        when(repasseRepository.buscarParaAlterar(1L)).thenReturn(Optional.of(pendente()));

        var pago = service().pagar(1L, FormaPagamento.PIX);

        assertThat(pago.isPago()).isTrue();
        assertThat(pago.getCaixaId()).isNull();
        verify(caixaRepository, never()).findByNegocioAndFechadoEmIsNull(any());
    }

    /** Mesma entrega paga duas vezes: a segunda é recusada. */
    @Test
    void repasseJaPagoNaoEhPagoDeNovo() {
        var pago = pendente();
        pago.pagar(FormaPagamento.PIX, null, MEIO_DIA, "ana");
        when(repasseRepository.buscarParaAlterar(1L)).thenReturn(Optional.of(pago));

        assertThatThrownBy(() -> service().pagar(1L, FormaPagamento.PIX))
                .isInstanceOf(RegraDeNegocioException.class)
                .hasMessage("Este repasse já foi pago.");
    }

    @Test
    void repasseCanceladoNaoTemPagamento() {
        var cancelado = pendente();
        cancelado.cancelar();
        when(repasseRepository.buscarParaAlterar(1L)).thenReturn(Optional.of(cancelado));

        assertThatThrownBy(() -> service().pagar(1L, FormaPagamento.PIX))
                .isInstanceOf(RegraDeNegocioException.class)
                .hasMessage("Esta entrega foi cancelada e não tem repasse a pagar.");
    }

    // ── Pagar várias de uma vez (tudo ou nada) ───────────────────────────────

    @Test
    void pagarVariasPagaTodasETravaEmOrdemCrescente() {
        var r1 = pendente("3.00");
        var r2 = pendente("5.00");
        var r3 = pendente("7.00");
        when(repasseRepository.buscarParaAlterar(1L)).thenReturn(Optional.of(r1));
        when(repasseRepository.buscarParaAlterar(2L)).thenReturn(Optional.of(r2));
        when(repasseRepository.buscarParaAlterar(3L)).thenReturn(Optional.of(r3));

        var pagos = service().pagarVarios(java.util.List.of(3L, 1L, 2L), FormaPagamento.PIX);

        assertThat(pagos).hasSize(3).allMatch(RepasseEntrega::isPago);
        // Pedidos fora de ordem, travas em ordem crescente: evita o deadlock.
        var ordem = inOrder(repasseRepository);
        ordem.verify(repasseRepository).buscarParaAlterar(1L);
        ordem.verify(repasseRepository).buscarParaAlterar(2L);
        ordem.verify(repasseRepository).buscarParaAlterar(3L);
    }

    /** O coração do "tudo ou nada": uma já paga na lista, e NENHUMA é paga. */
    @Test
    void seUmaJaFoiPagaNenhumaEhPaga() {
        var pendente = pendente();
        var jaPago = pendente();
        jaPago.pagar(FormaPagamento.PIX, null, MEIO_DIA, "ana");
        when(repasseRepository.buscarParaAlterar(1L)).thenReturn(Optional.of(pendente));
        when(repasseRepository.buscarParaAlterar(2L)).thenReturn(Optional.of(jaPago));

        assertThatThrownBy(() -> service().pagarVarios(java.util.List.of(1L, 2L), FormaPagamento.PIX))
                .isInstanceOf(RegraDeNegocioException.class)
                .hasMessageContaining("Nenhuma entrega foi paga")
                .hasMessageContaining("a entrega 2 já foi paga");
        assertThat(pendente.isPago()).isFalse();
        verify(auditoria, never()).registrar(any(), any(), any(), any());
    }

    @Test
    void seUmaFoiCanceladaNenhumaEhPaga() {
        var pendente = pendente();
        var cancelado = pendente();
        cancelado.cancelar();
        when(repasseRepository.buscarParaAlterar(1L)).thenReturn(Optional.of(pendente));
        when(repasseRepository.buscarParaAlterar(2L)).thenReturn(Optional.of(cancelado));

        assertThatThrownBy(() -> service().pagarVarios(java.util.List.of(1L, 2L), FormaPagamento.PIX))
                .isInstanceOf(RegraDeNegocioException.class)
                .hasMessageContaining("a entrega 2 foi cancelada");
        assertThat(pendente.isPago()).isFalse();
    }

    /** Em dinheiro sem caixa aberto: recusa ANTES de pagar qualquer uma. */
    @Test
    void emDinheiroSemCaixaAbertoNenhumaEhPaga() {
        var r1 = pendente();
        var r2 = pendente();
        when(repasseRepository.buscarParaAlterar(1L)).thenReturn(Optional.of(r1));
        when(repasseRepository.buscarParaAlterar(2L)).thenReturn(Optional.of(r2));
        when(caixaRepository.findByNegocioAndFechadoEmIsNull(Negocio.PASTELARIA)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().pagarVarios(java.util.List.of(1L, 2L), FormaPagamento.DINHEIRO))
                .isInstanceOf(RegraDeNegocioException.class)
                .hasMessage("Abra o caixa da pastelaria antes de pagar em dinheiro.");
        assertThat(r1.isPago()).isFalse();
        assertThat(r2.isPago()).isFalse();
    }

    @Test
    void listaVaziaOuComRepetidaEhRecusadaSemTocarNoBanco() {
        assertThatThrownBy(() -> service().pagarVarios(java.util.List.of(), FormaPagamento.PIX))
                .isInstanceOf(RegraDeNegocioException.class)
                .hasMessage("Escolha ao menos uma entrega para pagar.");
        assertThatThrownBy(() -> service().pagarVarios(java.util.List.of(1L, 1L), FormaPagamento.PIX))
                .isInstanceOf(RegraDeNegocioException.class)
                .hasMessage("A mesma entrega foi escolhida mais de uma vez.");
        verifyNoInteractions(repasseRepository);
    }

    // ── Cancelamento da venda ────────────────────────────────────────────────

    @Test
    void cancelarVendaComRepassePendenteCancelaORepasse() {
        var repasse = pendente();
        when(repasseRepository.buscarPorVendaParaAlterar(1L)).thenReturn(Optional.of(repasse));

        service().cancelarDaVenda(1L);

        assertThat(repasse.isCancelado()).isTrue();
    }

    @Test
    void cancelarVendaJaRepassadaEhRecusado() {
        var pago = pendente();
        pago.pagar(FormaPagamento.PIX, null, MEIO_DIA, "ana");
        when(repasseRepository.buscarPorVendaParaAlterar(1L)).thenReturn(Optional.of(pago));

        assertThatThrownBy(() -> service().cancelarDaVenda(1L))
                .isInstanceOf(RegraDeNegocioException.class)
                .hasMessage("Esta entrega já foi repassada ao entregador e não pode ser cancelada.");
    }
}
