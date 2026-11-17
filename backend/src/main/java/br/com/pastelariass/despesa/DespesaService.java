package br.com.pastelariass.despesa;

import br.com.pastelariass.auditoria.AcaoAuditoria;
import br.com.pastelariass.auditoria.AuditoriaService;
import br.com.pastelariass.caixa.CaixaRepository;
import br.com.pastelariass.comum.Negocio;
import br.com.pastelariass.comum.RecursoNaoEncontradoException;
import br.com.pastelariass.comum.RegraDeNegocioException;
import br.com.pastelariass.comum.Validacoes;
import br.com.pastelariass.venda.FormaPagamento;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.function.Predicate;

/**
 * Regras das despesas e das compras de peixe.
 *
 * Despesa paga em dinheiro sai da gaveta do caixa aberto do próprio negócio.
 * Paga por Pix ou cartão, não mexe na gaveta. Pendente não mexe em nada até
 * ser paga.
 */
@Service
public class DespesaService {

    static final int TAMANHO_MAXIMO_DESCRICAO = 120;
    static final int TAMANHO_MAXIMO_RESPONSAVEL = 60;
    static final int TAMANHO_MAXIMO_OBSERVACAO = 500;
    static final int MAXIMO_ITENS = 100;

    private static final ZoneId ZONA_DA_CASA = ZoneId.of("America/Sao_Paulo");

    /** Totais de um mês para um negócio. O preço médio só usa compras de peixe com peso. */
    public record ResumoDespesas(Negocio negocio, YearMonth mes, BigDecimal totalGasto, BigDecimal totalPago,
                                 BigDecimal totalPendente, BigDecimal kgComprados, BigDecimal precoMedioPorKg) {
    }

    private final DespesaRepository despesaRepository;
    private final DespesaItemRepository itemRepository;
    private final CaixaRepository caixaRepository;
    private final AuditoriaService auditoria;
    private final Clock relogio;

    public DespesaService(DespesaRepository despesaRepository, DespesaItemRepository itemRepository,
                          CaixaRepository caixaRepository, AuditoriaService auditoria, Clock relogio) {
        this.despesaRepository = despesaRepository;
        this.itemRepository = itemRepository;
        this.caixaRepository = caixaRepository;
        this.auditoria = auditoria;
        this.relogio = relogio;
    }

    /**
     * Registra uma despesa. Com itens detalhados (só em ingredientes), o valor é a
     * soma dos itens; se o valor também vier no pedido, precisa bater com essa soma.
     */
    @Transactional
    public Despesa registrar(CriacaoDespesaRequest dados) {
        if (dados.negocio() == null || dados.categoria() == null || dados.data() == null) {
            throw new RegraDeNegocioException("Informe o negócio, a categoria e a data da despesa.");
        }
        var descricao = Validacoes.texto(dados.descricao(), TAMANHO_MAXIMO_DESCRICAO, "A descrição");
        var responsavel = Validacoes.texto(dados.responsavel(), TAMANHO_MAXIMO_RESPONSAVEL, "O responsável");
        validarCategoria(dados.negocio(), dados.categoria());
        var peso = validarPeso(dados.categoria(), dados.pesoKg());
        if (dados.data().isAfter(hoje())) {
            throw new RegraDeNegocioException("A data da despesa não pode estar no futuro.");
        }
        var itens = itensDoPedido(dados.categoria(), dados.itens());
        var valor = itens.isEmpty() ? valorDaDespesa(dados.valor()) : valorDosItens(itens, dados.valor());

        var caixaId = caixaParaPagamento(dados.negocio(), dados.formaPagamento());
        var usuario = auditoria.quemEstaLogado();
        var despesa = new Despesa(dados.negocio(), descricao, dados.categoria(), valor, dados.data(),
                responsavel, dados.formaPagamento(), caixaId, peso, observacao(dados.observacao()),
                agora(), usuario);
        var salva = despesaRepository.saveAndFlush(despesa);
        for (var item : itens) {
            itemRepository.save(new DespesaItem(salva.getId(), item.descricao().strip(), item.quantidade(),
                    item.unidade(), item.precoUnitario()));
        }
        auditoria.registrar(usuario, AcaoAuditoria.DESPESA_REGISTRADA, descricao,
                "negocio=" + dados.negocio() + ", valor=" + valor + ", paga=" + salva.estaPaga());
        return salva;
    }

    /** Os itens detalhados da despesa, na ordem em que foram informados. */
    @Transactional(readOnly = true)
    public List<DespesaItem> itensDe(Long despesaId) {
        return itemRepository.findByDespesaIdOrderByIdAsc(despesaId);
    }

    /** Confere cada linha do pedido. Só compra de ingredientes aceita itens. */
    private List<ItemDespesaRequest> itensDoPedido(CategoriaDespesa categoria, List<ItemDespesaRequest> itens) {
        if (itens == null || itens.isEmpty()) {
            return List.of();
        }
        if (categoria != CategoriaDespesa.INGREDIENTES) {
            throw new RegraDeNegocioException("Itens detalhados só se informam em despesa de ingredientes.");
        }
        if (itens.size() > MAXIMO_ITENS) {
            throw new RegraDeNegocioException("Uma despesa aceita no máximo " + MAXIMO_ITENS + " itens.");
        }
        for (var item : itens) {
            Validacoes.texto(item.descricao(), TAMANHO_MAXIMO_DESCRICAO, "A descrição do item");
            if (item.quantidade().signum() <= 0) {
                throw new RegraDeNegocioException("A quantidade de cada item deve ser maior que zero.");
            }
        }
        return itens;
    }

    /** Soma os itens. Se o pedido também trouxe valor, ele precisa bater com a soma. */
    private static BigDecimal valorDosItens(List<ItemDespesaRequest> itens, BigDecimal valorInformado) {
        var soma = itens.stream()
                .map(item -> DespesaItem.calcularTotal(item.quantidade(), item.precoUnitario()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (soma.signum() == 0) {
            throw new RegraDeNegocioException("O total dos itens deve ser maior que zero.");
        }
        if (valorInformado != null && valorInformado.compareTo(soma) != 0) {
            throw new RegraDeNegocioException("O valor informado não bate com a soma dos itens (R$ " + soma + ").");
        }
        return soma;
    }

    /** Paga uma despesa pendente. A despesa é travada, então não é paga duas vezes. */
    @Transactional
    public Despesa pagar(Long id, PagamentoDespesaRequest dados) {
        var despesa = despesaRepository.buscarParaAlterar(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Despesa não encontrada."));
        if (despesa.estaPaga()) {
            throw new RegraDeNegocioException("Esta despesa já foi paga.");
        }
        if (dados.formaPagamento() == null) {
            throw new RegraDeNegocioException("Informe a forma de pagamento.");
        }
        var caixaId = caixaParaPagamento(despesa.getNegocio(), dados.formaPagamento());
        despesa.pagar(dados.formaPagamento(), caixaId, agora());
        auditoria.registrar(auditoria.quemEstaLogado(), AcaoAuditoria.DESPESA_PAGA,
                "despesa " + id, "forma=" + dados.formaPagamento());
        return despesa;
    }

    /** Despesas com data no mês, do negócio informado ou de todos. */
    @Transactional(readOnly = true)
    public List<Despesa> listar(YearMonth mes, Negocio negocio) {
        var inicio = mes.atDay(1);
        var fim = mes.atEndOfMonth();
        return negocio == null
                ? despesaRepository.findByDataDespesaBetweenOrderByDataDespesaDesc(inicio, fim)
                : despesaRepository.findByNegocioAndDataDespesaBetweenOrderByDataDespesaDesc(negocio, inicio, fim);
    }

    @Transactional(readOnly = true)
    public ResumoDespesas resumo(Negocio negocio, YearMonth mes) {
        var despesas = listar(mes, negocio);

        var totalGasto = somar(despesas, d -> true);
        var totalPago = somar(despesas, Despesa::estaPaga);
        var totalPendente = somar(despesas, d -> !d.estaPaga());

        var compras = despesas.stream()
                .filter(d -> d.getCategoria() == CategoriaDespesa.COMPRA_DE_PEIXE && d.getPesoKg() != null)
                .toList();
        var kg = compras.stream().map(Despesa::getPesoKg).reduce(BigDecimal.ZERO, BigDecimal::add);
        var valorDasCompras = somar(compras, d -> true);
        var precoMedio = kg.signum() == 0 ? null : valorDasCompras.divide(kg, 2, RoundingMode.HALF_UP);

        return new ResumoDespesas(negocio, mes, totalGasto, totalPago, totalPendente, kg, precoMedio);
    }

    /**
     * Se a despesa for paga em dinheiro, devolve o caixa aberto do negócio (travado).
     * Sem dinheiro, não há caixa envolvido.
     */
    private Long caixaParaPagamento(Negocio negocio, FormaPagamento forma) {
        if (forma != FormaPagamento.DINHEIRO) {
            return null;
        }
        var caixa = caixaRepository.findByNegocioAndFechadoEmIsNull(negocio)
                .orElseThrow(() -> new RegraDeNegocioException(
                        "Abra o caixa da " + nomeDoNegocio(negocio) + " antes de pagar em dinheiro."));
        return caixaRepository.buscarParaAlterar(caixa.getId()).orElseThrow().getId();
    }

    private static void validarCategoria(Negocio negocio, CategoriaDespesa categoria) {
        if (categoria == CategoriaDespesa.COMPRA_DE_PEIXE && negocio != Negocio.PEIXARIA) {
            throw new RegraDeNegocioException("Compra de peixe é uma despesa da peixaria.");
        }
    }

    /** Peso só faz sentido em compra de peixe, para calcular o preço médio por kg. */
    private static BigDecimal validarPeso(CategoriaDespesa categoria, BigDecimal peso) {
        if (peso == null) {
            return null;
        }
        if (categoria != CategoriaDespesa.COMPRA_DE_PEIXE) {
            throw new RegraDeNegocioException("O peso só se informa em compra de peixe.");
        }
        return peso;
    }

    private static BigDecimal valorDaDespesa(BigDecimal valor) {
        var valorValido = Validacoes.valorMonetario(valor, "O valor");
        if (valorValido.signum() == 0) {
            throw new RegraDeNegocioException("O valor deve ser maior que zero.");
        }
        return valorValido;
    }

    private static BigDecimal somar(List<Despesa> despesas, Predicate<Despesa> filtro) {
        return despesas.stream()
                .filter(filtro)
                .map(Despesa::getValor)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static String nomeDoNegocio(Negocio negocio) {
        return negocio == Negocio.PEIXARIA ? "peixaria" : "pastelaria";
    }

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

    private LocalDate hoje() {
        return LocalDate.now(relogio.withZone(ZONA_DA_CASA));
    }

    private Instant agora() {
        return Instant.now(relogio).truncatedTo(ChronoUnit.MICROS);
    }
}
