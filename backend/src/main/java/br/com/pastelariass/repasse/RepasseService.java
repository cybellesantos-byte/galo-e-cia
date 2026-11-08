package br.com.pastelariass.repasse;

import br.com.pastelariass.auditoria.AcaoAuditoria;
import br.com.pastelariass.auditoria.AuditoriaService;
import br.com.pastelariass.caixa.CaixaRepository;
import br.com.pastelariass.comum.Negocio;
import br.com.pastelariass.comum.RecursoNaoEncontradoException;
import br.com.pastelariass.comum.RegraDeNegocioException;
import br.com.pastelariass.localidade.LocalidadeRepository;
import br.com.pastelariass.venda.FormaPagamento;
import br.com.pastelariass.venda.Venda;
import br.com.pastelariass.venda.VendaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * Repasses ao entregador.
 *
 * O valor de cada repasse é o MAIOR entre a taxa padrão da localidade e a taxa
 * cobrada do cliente. Assim o entregador recebe o combinado, e se a pastelaria
 * cobrar mais, o extra é dele.
 */
@Service
public class RepasseService {

    public record ResumoRepasses(LocalDate inicio, LocalDate fim, BigDecimal totalPendente, BigDecimal totalPago) {
    }

    /** Limite de entregas num pagamento só: uma semana movimentada cabe com folga. */
    static final int MAXIMO_POR_PAGAMENTO = 500;

    private final RepasseRepository repasseRepository;
    private final CaixaRepository caixaRepository;
    private final VendaRepository vendaRepository;
    private final LocalidadeRepository localidadeRepository;
    private final AuditoriaService auditoria;
    private final Clock relogio;

    public RepasseService(RepasseRepository repasseRepository, CaixaRepository caixaRepository,
                          VendaRepository vendaRepository, LocalidadeRepository localidadeRepository,
                          AuditoriaService auditoria, Clock relogio) {
        this.repasseRepository = repasseRepository;
        this.caixaRepository = caixaRepository;
        this.vendaRepository = vendaRepository;
        this.localidadeRepository = localidadeRepository;
        this.auditoria = auditoria;
        this.relogio = relogio;
    }

    /** Chamado pela venda de entrega, na mesma transação. */
    @Transactional
    public RepasseEntrega gerar(Long vendaId, BigDecimal taxaPadrao, BigDecimal taxaCobrada, LocalDate dataEntrega) {
        var valor = taxaPadrao.max(taxaCobrada);
        return repasseRepository.saveAndFlush(new RepasseEntrega(vendaId, valor, dataEntrega, agora()));
    }

    /**
     * Cancela o repasse de uma venda cancelada. Se o entregador já recebeu, a
     * venda não pode ser cancelada: o dinheiro já saiu.
     */
    @Transactional
    public void cancelarDaVenda(Long vendaId) {
        repasseRepository.buscarPorVendaParaAlterar(vendaId).ifPresent(repasse -> {
            if (repasse.isPago()) {
                throw new RegraDeNegocioException("Esta entrega já foi repassada ao entregador e não pode ser cancelada.");
            }
            repasse.cancelar();
        });
    }

    /** Paga o repasse. Em dinheiro, sai da gaveta do caixa aberto da pastelaria. */
    @Transactional
    public RepasseEntrega pagar(Long id, FormaPagamento formaPagamento) {
        var repasse = repasseRepository.buscarParaAlterar(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Repasse não encontrado."));
        if (repasse.isCancelado()) {
            throw new RegraDeNegocioException("Esta entrega foi cancelada e não tem repasse a pagar.");
        }
        if (repasse.isPago()) {
            throw new RegraDeNegocioException("Este repasse já foi pago.");
        }
        var caixaId = caixaParaPagamento(formaPagamento);
        var usuario = auditoria.quemEstaLogado();
        repasse.pagar(formaPagamento, caixaId, agora(), usuario);
        auditoria.registrar(usuario, AcaoAuditoria.REPASSE_PAGO, "repasse " + id,
                "valor=" + repasse.getValor() + ", forma=" + formaPagamento);
        return repasse;
    }

    /**
     * Paga várias entregas de uma vez (o acerto da semana), no esquema TUDO OU NADA:
     * ou todas ficam pagas, ou nenhuma fica.
     *
     * Como garante isso:
     *  1. Trava TODAS as entregas escolhidas e confere cada uma ANTES de pagar
     *     qualquer uma. Se uma estiver paga, cancelada ou não existir, para aqui,
     *     sem ter mudado nada.
     *  2. Só então paga todas, dentro da mesma transação. Se algo falhar no meio,
     *     o banco desfaz tudo (rollback).
     *
     * As travas são pegas em ordem crescente de id. Se dois pagamentos tentarem
     * as mesmas entregas ao mesmo tempo, os dois pedem as travas na mesma ordem:
     * um espera o outro, em vez de cada um segurar uma trava que o outro quer
     * (o "abraço mortal", ou deadlock).
     */
    @Transactional
    public List<RepasseEntrega> pagarVarios(List<Long> ids, FormaPagamento formaPagamento) {
        if (ids == null || ids.isEmpty()) {
            throw new RegraDeNegocioException("Escolha ao menos uma entrega para pagar.");
        }
        if (ids.size() > MAXIMO_POR_PAGAMENTO) {
            throw new RegraDeNegocioException("Pague no máximo " + MAXIMO_POR_PAGAMENTO + " entregas de uma vez.");
        }
        if (new HashSet<>(ids).size() != ids.size()) {
            throw new RegraDeNegocioException("A mesma entrega foi escolhida mais de uma vez.");
        }
        if (formaPagamento == null) {
            throw new RegraDeNegocioException("Informe a forma de pagamento.");
        }

        // 1. Trava e confere todas, antes de mudar qualquer coisa.
        var repasses = new ArrayList<RepasseEntrega>();
        var problemas = new ArrayList<String>();
        for (var id : ids.stream().sorted().toList()) {
            var repasse = repasseRepository.buscarParaAlterar(id)
                    .orElseThrow(() -> new RecursoNaoEncontradoException("Repasse " + id + " não encontrado."));
            if (repasse.isPago()) {
                problemas.add("a entrega " + id + " já foi paga");
            } else if (repasse.isCancelado()) {
                problemas.add("a entrega " + id + " foi cancelada");
            }
            repasses.add(repasse);
        }
        if (!problemas.isEmpty()) {
            throw new RegraDeNegocioException("Nenhuma entrega foi paga: " + String.join("; ", problemas)
                    + ". Atualize a lista e tente de novo.");
        }

        // 2. Paga todas, com o mesmo caixa (se em dinheiro), a mesma hora e a mesma pessoa.
        var caixaId = caixaParaPagamento(formaPagamento);
        var usuario = auditoria.quemEstaLogado();
        var quando = agora();
        for (var repasse : repasses) {
            repasse.pagar(formaPagamento, caixaId, quando, usuario);
            auditoria.registrar(usuario, AcaoAuditoria.REPASSE_PAGO, "repasse " + repasse.getId(),
                    "valor=" + repasse.getValor() + ", forma=" + formaPagamento + ", em lote de " + repasses.size());
        }
        return repasses;
    }

    /**
     * O nome do local de cada entrega (Tanques, Saco...), por id da venda.
     * Venda sem local fica fora do mapa.
     */
    @Transactional(readOnly = true)
    public Map<Long, String> locaisDasVendas(Collection<Long> vendaIds) {
        var vendas = vendaRepository.findAllById(vendaIds);
        var localidadeIds = vendas.stream().map(Venda::getLocalidadeId).filter(Objects::nonNull).distinct().toList();
        var nomes = new HashMap<Long, String>();
        localidadeRepository.findAllById(localidadeIds).forEach(l -> nomes.put(l.getId(), l.getNome()));

        var resultado = new HashMap<Long, String>();
        for (var venda : vendas) {
            var nome = nomes.get(venda.getLocalidadeId());
            if (nome != null) {
                resultado.put(venda.getId(), nome);
            }
        }
        return resultado;
    }

    @Transactional(readOnly = true)
    public List<RepasseEntrega> listar(LocalDate inicio, LocalDate fim) {
        return repasseRepository.findByDataEntregaBetweenOrderByDataEntregaDesc(inicio, fim);
    }

    /** Totais do período: o que falta pagar e o que já foi pago. Cancelados não entram. */
    @Transactional(readOnly = true)
    public ResumoRepasses resumo(LocalDate inicio, LocalDate fim) {
        var repasses = listar(inicio, fim);
        var pendente = somar(repasses, r -> r.getSituacao() == SituacaoRepasse.PENDENTE);
        var pago = somar(repasses, RepasseEntrega::isPago);
        return new ResumoRepasses(inicio, fim, pendente, pago);
    }

    private Long caixaParaPagamento(FormaPagamento forma) {
        if (forma != FormaPagamento.DINHEIRO) {
            return null;
        }
        var caixa = caixaRepository.findByNegocioAndFechadoEmIsNull(Negocio.PASTELARIA)
                .orElseThrow(() -> new RegraDeNegocioException(
                        "Abra o caixa da pastelaria antes de pagar em dinheiro."));
        return caixaRepository.buscarParaAlterar(caixa.getId()).orElseThrow().getId();
    }

    private static BigDecimal somar(List<RepasseEntrega> repasses, Predicate<RepasseEntrega> filtro) {
        return repasses.stream()
                .filter(filtro)
                .map(RepasseEntrega::getValor)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private Instant agora() {
        return Instant.now(relogio).truncatedTo(ChronoUnit.MICROS);
    }
}
