package br.com.pastelariass.caixa;

import br.com.pastelariass.auditoria.AcaoAuditoria;
import br.com.pastelariass.auditoria.AuditoriaService;
import br.com.pastelariass.comum.Negocio;
import br.com.pastelariass.comum.RecursoNaoEncontradoException;
import br.com.pastelariass.comum.RegraDeNegocioException;
import br.com.pastelariass.comum.Validacoes;
import br.com.pastelariass.despesa.DespesaRepository;
import br.com.pastelariass.repasse.RepasseRepository;
import br.com.pastelariass.venda.FormaPagamento;
import br.com.pastelariass.venda.VendaRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Regras do caixa.
 *
 * Esperado na gaveta = saldo inicial + reforços − sangrias + vendas em dinheiro
 * − despesas pagas do caixa − repasses pagos do caixa (ver ComposicaoEsperado).
 * O Pix nunca entra aqui: ele não passa pela gaveta.
 */
@Service
public class CaixaService {

    static final int TAMANHO_MAXIMO_OBSERVACAO = 500;

    private static final String JA_TEM_CAIXA_ABERTO = "Já existe um caixa aberto para este negócio.";
    private static final String CAIXA_FECHADO = "Este caixa já está fechado e não pode mais ser alterado.";

    private final CaixaRepository caixaRepository;
    private final MovimentacaoCaixaRepository movimentacaoRepository;
    private final VendaRepository vendaRepository;
    private final DespesaRepository despesaRepository;
    private final RepasseRepository repasseRepository;
    private final AuditoriaService auditoria;
    private final Clock relogio;

    public CaixaService(CaixaRepository caixaRepository, MovimentacaoCaixaRepository movimentacaoRepository,
                        VendaRepository vendaRepository, DespesaRepository despesaRepository,
                        RepasseRepository repasseRepository, AuditoriaService auditoria, Clock relogio) {
        this.caixaRepository = caixaRepository;
        this.movimentacaoRepository = movimentacaoRepository;
        this.vendaRepository = vendaRepository;
        this.despesaRepository = despesaRepository;
        this.repasseRepository = repasseRepository;
        this.auditoria = auditoria;
        this.relogio = relogio;
    }

    @Transactional
    public Caixa abrir(Negocio negocio, BigDecimal saldoInicial) {
        if (negocio == null) {
            throw new RegraDeNegocioException("Informe o negócio: PASTELARIA ou PEIXARIA.");
        }
        var saldo = Validacoes.valorMonetario(saldoInicial, "O saldo inicial");
        if (caixaRepository.findByNegocioAndFechadoEmIsNull(negocio).isPresent()) {
            throw new RegraDeNegocioException(JA_TEM_CAIXA_ABERTO);
        }

        var usuario = auditoria.quemEstaLogado();
        try {
            var caixa = caixaRepository.saveAndFlush(new Caixa(negocio, saldo, agora(), usuario));
            auditoria.registrar(usuario, AcaoAuditoria.CAIXA_ABERTO, negocio.name(),
                    "saldo inicial=" + saldo);
            return caixa;
        } catch (DataIntegrityViolationException e) {
            // Duas aberturas ao mesmo tempo: o índice único deixa passar só uma.
            throw new RegraDeNegocioException(JA_TEM_CAIXA_ABERTO);
        }
    }

    @Transactional
    public Caixa reforco(Long caixaId, BigDecimal valor, String observacao) {
        var caixa = buscarAberto(caixaId);
        var quantia = valorDaMovimentacao(valor);
        registrar(caixa, TipoMovimentacao.REFORCO, quantia, observacao, AcaoAuditoria.CAIXA_REFORCO);
        return caixa;
    }

    @Transactional
    public Caixa sangria(Long caixaId, BigDecimal valor, String observacao) {
        var caixa = buscarAberto(caixaId);
        var quantia = valorDaMovimentacao(valor);
        if (quantia.compareTo(calcularEsperado(caixa)) > 0) {
            throw new RegraDeNegocioException("Não há dinheiro suficiente na gaveta para essa sangria.");
        }
        registrar(caixa, TipoMovimentacao.SANGRIA, quantia, observacao, AcaoAuditoria.CAIXA_SANGRIA);
        return caixa;
    }

    /**
     * Fecha o caixa: grava o esperado e a diferença entre o contado e o esperado.
     * Depois disto, nada mais entra nesse caixa.
     */
    @Transactional
    public Caixa fechar(Long caixaId, BigDecimal valorContado, String observacao) {
        var caixa = buscarAberto(caixaId);
        var contado = Validacoes.valorMonetario(valorContado, "O valor contado");
        var obs = observacao(observacao);
        var esperado = calcularEsperado(caixa);
        var usuario = auditoria.quemEstaLogado();

        caixa.fechar(usuario, agora(), esperado, contado, obs);
        auditoria.registrar(usuario, AcaoAuditoria.CAIXA_FECHADO, caixa.getNegocio().name(),
                "esperado=" + esperado + ", contado=" + contado + ", diferenca=" + caixa.getDiferenca());
        return caixa;
    }

    /** Para um caixa aberto, o esperado do momento. Para um fechado, o valor gravado no fechamento. */
    @Transactional(readOnly = true)
    public BigDecimal esperadoAtual(Caixa caixa) {
        return caixa.estaAberto() ? calcularEsperado(caixa) : caixa.getEsperado();
    }

    @Transactional(readOnly = true)
    public List<MovimentacaoCaixa> movimentacoes(Long caixaId) {
        return movimentacaoRepository.findByCaixaIdOrderByQuandoAsc(caixaId);
    }

    @Transactional(readOnly = true)
    public Caixa buscarPorId(Long id) {
        return caixaRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Caixa não encontrado."));
    }

    @Transactional(readOnly = true)
    public Caixa caixaAberto(Negocio negocio) {
        return caixaRepository.findByNegocioAndFechadoEmIsNull(negocio)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Não há caixa aberto para este negócio."));
    }

    @Transactional(readOnly = true)
    public List<Caixa> listar(Negocio negocio) {
        var ordem = Sort.by(Sort.Direction.DESC, "abertoEm");
        return negocio == null ? caixaRepository.findAll(ordem) : caixaRepository.findByNegocio(negocio, ordem);
    }

    /** Busca o caixa travado para alteração e recusa se ele já estiver fechado. */
    private Caixa buscarAberto(Long id) {
        var caixa = caixaRepository.buscarParaAlterar(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Caixa não encontrado."));
        if (!caixa.estaAberto()) {
            throw new RegraDeNegocioException(CAIXA_FECHADO);
        }
        return caixa;
    }

    private BigDecimal calcularEsperado(Caixa caixa) {
        return calcularComposicao(caixa).esperado();
    }

    /**
     * A conta aberta do esperado, para a tela de fechamento mostrar parcela por
     * parcela. Só faz sentido com o caixa aberto: fechado, o esperado que vale é
     * o gravado no fechamento.
     */
    @Transactional(readOnly = true)
    public ComposicaoEsperado composicao(Caixa caixa) {
        return caixa.estaAberto() ? calcularComposicao(caixa) : null;
    }

    /**
     * Soma, separadas, as parcelas que mexem na gaveta: reforços, sangrias, vendas
     * em dinheiro (pelo total, sem o troco), despesas pagas do caixa e repasses
     * pagos do caixa. Vendas no Pix e canceladas não entram; despesas pagas por
     * Pix ou cartão também não.
     */
    private ComposicaoEsperado calcularComposicao(Caixa caixa) {
        var reforcos = BigDecimal.ZERO;
        var sangrias = BigDecimal.ZERO;
        for (var movimento : movimentacaoRepository.findByCaixaIdOrderByQuandoAsc(caixa.getId())) {
            if (movimento.getTipo() == TipoMovimentacao.REFORCO) {
                reforcos = reforcos.add(movimento.getValor());
            } else {
                sangrias = sangrias.add(movimento.getValor());
            }
        }
        var vendasDinheiro = BigDecimal.ZERO;
        for (var venda : vendaRepository.findByCaixaIdAndFormaPagamentoAndCanceladaFalse(
                caixa.getId(), FormaPagamento.DINHEIRO)) {
            vendasDinheiro = vendasDinheiro.add(venda.getTotal());
        }
        var despesas = BigDecimal.ZERO;
        for (var despesa : despesaRepository.findByCaixaId(caixa.getId())) {
            despesas = despesas.add(despesa.getValor());
        }
        var repasses = BigDecimal.ZERO;
        for (var repasse : repasseRepository.findByCaixaId(caixa.getId())) {
            repasses = repasses.add(repasse.getValor());
        }
        return new ComposicaoEsperado(caixa.getSaldoInicial(), reforcos, sangrias, vendasDinheiro, despesas, repasses);
    }

    private void registrar(Caixa caixa, TipoMovimentacao tipo, BigDecimal valor, String observacao,
                           AcaoAuditoria acao) {
        var usuario = auditoria.quemEstaLogado();
        movimentacaoRepository.saveAndFlush(
                new MovimentacaoCaixa(caixa.getId(), tipo, valor, agora(), usuario, observacao(observacao)));
        auditoria.registrar(usuario, acao, caixa.getNegocio().name(), "valor=" + valor);
    }

    private static BigDecimal valorDaMovimentacao(BigDecimal valor) {
        var quantia = Validacoes.valorMonetario(valor, "O valor");
        if (quantia.signum() == 0) {
            throw new RegraDeNegocioException("O valor deve ser maior que zero.");
        }
        return quantia;
    }

    /** Observação opcional: vazia vira null; longa demais é recusada. */
    private static String observacao(String texto) {
        if (texto == null || texto.isBlank()) {
            return null;
        }
        var limpo = texto.strip();
        if (limpo.length() > TAMANHO_MAXIMO_OBSERVACAO) {
            throw new RegraDeNegocioException("A observação deve ter no máximo " + TAMANHO_MAXIMO_OBSERVACAO + " caracteres.");
        }
        return limpo;
    }

    /** Hora atual, sem nanossegundos: o banco guarda microssegundos. */
    private Instant agora() {
        return Instant.now(relogio).truncatedTo(ChronoUnit.MICROS);
    }
}
