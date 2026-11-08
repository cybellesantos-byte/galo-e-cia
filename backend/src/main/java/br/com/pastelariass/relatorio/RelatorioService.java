package br.com.pastelariass.relatorio;

import br.com.pastelariass.caixa.Caixa;
import br.com.pastelariass.caixa.CaixaRepository;
import br.com.pastelariass.comum.Negocio;
import br.com.pastelariass.comum.RegraDeNegocioException;
import br.com.pastelariass.despesa.CategoriaDespesa;
import br.com.pastelariass.despesa.Despesa;
import br.com.pastelariass.despesa.DespesaRepository;
import br.com.pastelariass.produto.Produto;
import br.com.pastelariass.produto.ProdutoRepository;
import br.com.pastelariass.produto.UnidadeVenda;
import br.com.pastelariass.repasse.RepasseService;
import br.com.pastelariass.venda.FormaPagamento;
import br.com.pastelariass.venda.Origem;
import br.com.pastelariass.venda.SituacaoPagamento;
import br.com.pastelariass.venda.TipoVenda;
import br.com.pastelariass.venda.Venda;
import br.com.pastelariass.venda.VendaItem;
import br.com.pastelariass.venda.VendaItemRepository;
import br.com.pastelariass.venda.VendaRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Relatórios de cada negócio e do conjunto.
 *
 * Definições (para os números baterem com a conta feita à mão):
 *  - faturamento: total de todas as vendas do período, sem as canceladas;
 *  - recebido: só as vendas com pagamento confirmado (pago);
 *  - pendente: vendas ainda sem pagamento confirmado (Pix ou cartão);
 *  - despesas: as do período, pagas e pendentes separadas.
 *
 * Tudo usa o dia da casa (America/Sao_Paulo) para dizer em que dia cai cada coisa.
 */
@Service
public class RelatorioService {

    static final int DIAS_MAXIMOS = 400;
    static final int TOP_PRODUTOS = 5;

    private static final ZoneId ZONA_DA_CASA = ZoneId.of("America/Sao_Paulo");

    /** Dados do período, carregados uma vez e usados por todas as seções. */
    private record Dados(List<Venda> todas, List<Venda> validas, Map<Long, List<VendaItem>> itensPorVenda,
                         Map<Long, Produto> produtos) {
    }

    private final VendaRepository vendaRepository;
    private final VendaItemRepository itemRepository;
    private final CaixaRepository caixaRepository;
    private final DespesaRepository despesaRepository;
    private final ProdutoRepository produtoRepository;
    private final RepasseService repasseService;

    public RelatorioService(VendaRepository vendaRepository, VendaItemRepository itemRepository,
                            CaixaRepository caixaRepository, DespesaRepository despesaRepository,
                            ProdutoRepository produtoRepository, RepasseService repasseService) {
        this.vendaRepository = vendaRepository;
        this.itemRepository = itemRepository;
        this.caixaRepository = caixaRepository;
        this.despesaRepository = despesaRepository;
        this.produtoRepository = produtoRepository;
        this.repasseService = repasseService;
    }

    // ── Relatórios por negócio ───────────────────────────────────────────────

    @Transactional(readOnly = true)
    public RelatorioPastelaria pastelaria(LocalDate inicio, LocalDate fim) {
        validarPeriodo(inicio, fim);
        var dados = carregar(Negocio.PASTELARIA, inicio, fim);
        var comum = comum(Negocio.PASTELARIA, inicio, fim, dados);
        return new RelatorioPastelaria(comum, taxasEntrega(dados), origens(dados), margens(dados),
                repasseService.resumo(inicio, fim));
    }

    @Transactional(readOnly = true)
    public RelatorioPeixaria peixaria(LocalDate inicio, LocalDate fim) {
        validarPeriodo(inicio, fim);
        var dados = carregar(Negocio.PEIXARIA, inicio, fim);
        var comum = comum(Negocio.PEIXARIA, inicio, fim, dados);
        var despesas = despesasDoPeriodo(Negocio.PEIXARIA, inicio, fim);

        var compras = compras(despesas.stream()
                .filter(d -> d.getCategoria() == CategoriaDespesa.COMPRA_DE_PEIXE).toList());
        var outras = somar(despesas, d -> d.getCategoria() != CategoriaDespesa.COMPRA_DE_PEIXE, Despesa::getValor);
        var resultado = comum.recebido().subtract(compras.total()).subtract(outras);

        return new RelatorioPeixaria(comum, vendasPorDia(dados), kgPorPeixe(dados), compras, outras,
                resultado, RESULTADO_APROXIMADO);
    }

    /** Os dois negócios somados: só os totais, sem as seções específicas de cada um. */
    @Transactional(readOnly = true)
    public RelatorioGeral geral(LocalDate inicio, LocalDate fim) {
        validarPeriodo(inicio, fim);
        var pastelaria = comum(Negocio.PASTELARIA, inicio, fim, carregar(Negocio.PASTELARIA, inicio, fim));
        var peixaria = comum(Negocio.PEIXARIA, inicio, fim, carregar(Negocio.PEIXARIA, inicio, fim));
        return new RelatorioGeral(inicio, fim,
                pastelaria.faturamento().add(peixaria.faturamento()),
                pastelaria.recebido().add(peixaria.recebido()),
                pastelaria.pendente().add(peixaria.pendente()),
                pastelaria.cancelado().add(peixaria.cancelado()),
                pastelaria.pix().add(peixaria.pix()),
                pastelaria.cartao().add(peixaria.cartao()),
                pastelaria.dinheiro().add(peixaria.dinheiro()),
                pastelaria.despesasPagas().add(peixaria.despesasPagas()),
                pastelaria.despesasPendentes().add(peixaria.despesasPendentes()));
    }

    /** Quanto a peixaria gastou em compras de peixe em cada mês do ano, com o preço médio por kg. */
    @Transactional(readOnly = true)
    public ComprasMensaisPeixe comprasMensaisPeixe(int ano) {
        var inicio = LocalDate.of(ano, 1, 1);
        var fim = LocalDate.of(ano, 12, 31);
        var compras = despesaRepository.findByNegocioAndDataDespesaBetweenOrderByDataDespesaDesc(
                        Negocio.PEIXARIA, inicio, fim).stream()
                .filter(d -> d.getCategoria() == CategoriaDespesa.COMPRA_DE_PEIXE)
                .collect(Collectors.groupingBy(d -> YearMonth.from(d.getDataDespesa())));

        var meses = new java.util.ArrayList<MesCompras>();
        for (int mes = 1; mes <= 12; mes++) {
            var ym = YearMonth.of(ano, mes);
            var doMes = compras.getOrDefault(ym, List.of());
            var resumo = compras(doMes);
            meses.add(new MesCompras(ym, resumo.total(), resumo.kg(), resumo.precoMedioPorKg()));
        }
        return new ComprasMensaisPeixe(ano, meses);
    }

    // ── Carregamento e validação ─────────────────────────────────────────────

    private Dados carregar(Negocio negocio, LocalDate inicio, LocalDate fim) {
        var todas = vendaRepository.listarDoNegocioNoPeriodo(negocio, inicioDoDia(inicio), inicioDoDia(fim.plusDays(1)));
        var validas = todas.stream().filter(v -> !v.isCancelada()).toList();
        var itensPorVenda = new HashMap<Long, List<VendaItem>>();
        var produtos = new HashMap<Long, Produto>();
        for (var venda : validas) {
            var itens = itemRepository.findByVendaIdOrderByIdAsc(venda.getId());
            itensPorVenda.put(venda.getId(), itens);
            for (var item : itens) {
                produtos.computeIfAbsent(item.getProdutoId(),
                        id -> produtoRepository.findById(id).orElse(null));
            }
        }
        return new Dados(todas, validas, itensPorVenda, produtos);
    }

    private static void validarPeriodo(LocalDate inicio, LocalDate fim) {
        if (inicio == null || fim == null) {
            throw new RegraDeNegocioException("Informe a data inicial e a data final.");
        }
        if (fim.isBefore(inicio)) {
            throw new RegraDeNegocioException("A data final não pode ser antes da inicial.");
        }
        if (inicio.plusDays(DIAS_MAXIMOS).isBefore(fim)) {
            throw new RegraDeNegocioException("O período não pode passar de " + DIAS_MAXIMOS + " dias.");
        }
    }

    private List<Despesa> despesasDoPeriodo(Negocio negocio, LocalDate inicio, LocalDate fim) {
        return despesaRepository.findByNegocioAndDataDespesaBetweenOrderByDataDespesaDesc(negocio, inicio, fim);
    }

    // ── Seções comuns ────────────────────────────────────────────────────────

    private RelatorioComum comum(Negocio negocio, LocalDate inicio, LocalDate fim, Dados dados) {
        var validas = dados.validas();
        var despesas = despesasDoPeriodo(negocio, inicio, fim);
        var fechamentos = fechamentos(negocio, inicio, fim);
        return new RelatorioComum(negocio, inicio, fim,
                validas.size(),
                dados.todas().size() - validas.size(),
                somar(validas, v -> true, Venda::getTotal),
                somar(validas, v -> v.getSituacaoPagamento() == SituacaoPagamento.PAGO, Venda::getTotal),
                somar(validas, v -> v.getSituacaoPagamento() == SituacaoPagamento.PENDENTE, Venda::getTotal),
                somar(dados.todas(), Venda::isCancelada, Venda::getTotal),
                somar(validas, v -> v.getFormaPagamento() == FormaPagamento.PIX, Venda::getTotal),
                somar(validas, v -> v.getFormaPagamento() == FormaPagamento.CARTAO, Venda::getTotal),
                somar(validas, v -> v.getFormaPagamento() == FormaPagamento.DINHEIRO, Venda::getTotal),
                somar(despesas, Despesa::estaPaga, Despesa::getValor),
                somar(despesas, d -> !d.estaPaga(), Despesa::getValor),
                somar(fechamentos, f -> true, FechamentoCaixa::diferenca),
                maisVendidos(dados),
                fechamentos);
    }

    /** Os cinco produtos que mais renderam, pelo total vendido. */
    private List<ProdutoVendido> maisVendidos(Dados dados) {
        var porProduto = new HashMap<String, ProdutoVendido>();
        for (var itens : dados.itensPorVenda().values()) {
            for (var item : itens) {
                porProduto.merge(item.getNomeProduto(),
                        new ProdutoVendido(item.getNomeProduto(), item.getQuantidade(), item.getTotal()),
                        (antigo, novo) -> new ProdutoVendido(antigo.nome(),
                                antigo.quantidade().add(novo.quantidade()), antigo.total().add(novo.total())));
            }
        }
        return porProduto.values().stream()
                .sorted(Comparator.comparing(ProdutoVendido::total).reversed())
                .limit(TOP_PRODUTOS)
                .toList();
    }

    private List<FechamentoCaixa> fechamentos(Negocio negocio, LocalDate inicio, LocalDate fim) {
        return caixaRepository.findByNegocio(negocio, Sort.by(Sort.Direction.ASC, "abertoEm")).stream()
                .filter(c -> !c.estaAberto())
                .filter(c -> noPeriodo(c.getFechadoEm(), inicio, fim))
                .map(c -> new FechamentoCaixa(c.getId(), c.getAbertoEm(), c.getFechadoEm(),
                        c.getEsperado(), c.getValorContado(), c.getDiferenca()))
                .toList();
    }

    // ── Seções da pastelaria ─────────────────────────────────────────────────

    private static SecaoTaxas taxasEntrega(Dados dados) {
        var entregas = dados.validas().stream().filter(v -> v.getTipo() == TipoVenda.ENTREGA).toList();
        return new SecaoTaxas(entregas.size(), somar(entregas, v -> true, Venda::getTaxaEntrega));
    }

    private static List<VendasPorOrigem> origens(Dados dados) {
        return java.util.Arrays.stream(Origem.values())
                .map(origem -> {
                    var doOrigem = dados.validas().stream().filter(v -> v.getOrigem() == origem).toList();
                    return new VendasPorOrigem(origem, doOrigem.size(), somar(doOrigem, v -> true, Venda::getTotal));
                })
                .toList();
    }

    /**
     * Margem = receita − custo estimado × quantidade, usando o custo GUARDADO EM CADA
     * ITEM no momento da venda (V12). Mudar o custo no cadastro não muda meses passados.
     *
     * Se algum item do produto no período não tem custo (produto sem custo cadastrado,
     * ou venda anterior à V12), a margem desse produto fica nula ("incompleta"), e não
     * zero: uma margem parcial pareceria maior do que é.
     */
    private List<MargemProduto> margens(Dados dados) {
        var porProduto = new HashMap<Long, List<VendaItem>>();
        for (var itens : dados.itensPorVenda().values()) {
            for (var item : itens) {
                porProduto.computeIfAbsent(item.getProdutoId(), id -> new java.util.ArrayList<>()).add(item);
            }
        }
        return porProduto.entrySet().stream()
                .map(entrada -> {
                    var itens = entrada.getValue();
                    var receita = somar(itens, i -> true, VendaItem::getTotal);
                    if (itens.stream().anyMatch(i -> i.getCustoUnitarioEstimado() == null)) {
                        return new MargemProduto(itens.get(0).getNomeProduto(), receita, null, null);
                    }
                    var custo = itens.stream()
                            .map(i -> i.getCustoUnitarioEstimado().multiply(i.getQuantidade()))
                            .reduce(BigDecimal.ZERO, BigDecimal::add)
                            .setScale(2, RoundingMode.HALF_UP);
                    return new MargemProduto(itens.get(0).getNomeProduto(), receita, custo, receita.subtract(custo));
                })
                .sorted(Comparator.comparing(MargemProduto::produto))
                .toList();
    }

    // ── Seções da peixaria ───────────────────────────────────────────────────

    private static List<VendasPorDia> vendasPorDia(Dados dados) {
        Map<LocalDate, List<Venda>> porDia = dados.validas().stream()
                .collect(Collectors.groupingBy(v -> dataDaCasa(v.getCriadoEm()), java.util.TreeMap::new,
                        Collectors.toList()));
        return porDia.entrySet().stream()
                .map(entrada -> new VendasPorDia(entrada.getKey(), entrada.getValue().size(),
                        somar(entrada.getValue(), v -> true, Venda::getTotal)))
                .toList();
    }

    /** Quilos vendidos por peixe (só produtos vendidos por kg). */
    private static List<KgPorPeixe> kgPorPeixe(Dados dados) {
        var porPeixe = new HashMap<String, KgPorPeixe>();
        for (var itens : dados.itensPorVenda().values()) {
            for (var item : itens) {
                var produto = dados.produtos().get(item.getProdutoId());
                if (produto == null || produto.getUnidadeVenda() != UnidadeVenda.KG) {
                    continue;
                }
                porPeixe.merge(item.getNomeProduto(),
                        new KgPorPeixe(item.getNomeProduto(), item.getQuantidade(), item.getTotal()),
                        (antigo, novo) -> new KgPorPeixe(antigo.peixe(),
                                antigo.kg().add(novo.kg()), antigo.total().add(novo.total())));
            }
        }
        return porPeixe.values().stream()
                .sorted(Comparator.comparing(KgPorPeixe::total).reversed())
                .toList();
    }

    private static ComprasPeixe compras(List<Despesa> compras) {
        var total = somar(compras, d -> true, Despesa::getValor);
        var comPeso = compras.stream().filter(d -> d.getPesoKg() != null).toList();
        var kg = comPeso.stream().map(Despesa::getPesoKg).reduce(BigDecimal.ZERO, BigDecimal::add);
        var valorComPeso = somar(comPeso, d -> true, Despesa::getValor);
        var precoMedio = kg.signum() == 0 ? null : valorComPeso.divide(kg, 2, RoundingMode.HALF_UP);
        return new ComprasPeixe(total, kg, precoMedio);
    }

    // ── Utilidades ───────────────────────────────────────────────────────────

    /** Soma o valor dos itens que passam pelo filtro. */
    private static <T> BigDecimal somar(Collection<T> itens, Predicate<T> filtro, Function<T, BigDecimal> valor) {
        return itens.stream()
                .filter(filtro)
                .map(valor)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static boolean noPeriodo(Instant quando, LocalDate inicio, LocalDate fim) {
        var dia = dataDaCasa(quando);
        return !dia.isBefore(inicio) && !dia.isAfter(fim);
    }

    private static LocalDate dataDaCasa(Instant quando) {
        return quando.atZone(ZONA_DA_CASA).toLocalDate();
    }

    private static Instant inicioDoDia(LocalDate dia) {
        return dia.atStartOfDay(ZONA_DA_CASA).toInstant();
    }

    static final String RESULTADO_APROXIMADO =
            "Resultado aproximado: o peixe comprado no fim do mês pode ser vendido no mês seguinte. "
                    + "Veja o comparativo mês a mês.";

    /** Um fechamento de caixa do período, para conferir as diferenças. */
    public record FechamentoCaixa(Long caixaId, Instant abertoEm, Instant fechadoEm, BigDecimal esperado,
                                  BigDecimal valorContado, BigDecimal diferenca) {
    }

    public record ProdutoVendido(String nome, BigDecimal quantidade, BigDecimal total) {
    }

    public record RelatorioComum(Negocio negocio, LocalDate inicio, LocalDate fim,
                                 int quantidadeVendas, int quantidadeCanceladas,
                                 BigDecimal faturamento, BigDecimal recebido, BigDecimal pendente,
                                 BigDecimal cancelado, BigDecimal pix, BigDecimal cartao, BigDecimal dinheiro,
                                 BigDecimal despesasPagas, BigDecimal despesasPendentes, BigDecimal diferencaCaixa,
                                 List<ProdutoVendido> maisVendidos, List<FechamentoCaixa> fechamentos) {
    }

    public record SecaoTaxas(int quantidadeEntregas, BigDecimal totalTaxas) {
    }

    public record VendasPorOrigem(Origem origem, int quantidade, BigDecimal total) {
    }

    /** custoEstimado e margem são nulos quando o produto não tem custo cadastrado. */
    public record MargemProduto(String produto, BigDecimal receita, BigDecimal custoEstimado, BigDecimal margem) {
    }

    public record RelatorioPastelaria(RelatorioComum comum, SecaoTaxas taxasEntrega,
                                      List<VendasPorOrigem> origens, List<MargemProduto> margens,
                                      RepasseService.ResumoRepasses repasses) {
    }

    public record VendasPorDia(LocalDate data, int quantidade, BigDecimal faturamento) {
    }

    public record KgPorPeixe(String peixe, BigDecimal kg, BigDecimal total) {
    }

    public record ComprasPeixe(BigDecimal total, BigDecimal kg, BigDecimal precoMedioPorKg) {
    }

    public record RelatorioPeixaria(RelatorioComum comum, List<VendasPorDia> vendasPorDia,
                                    List<KgPorPeixe> kgPorPeixe, ComprasPeixe compras, BigDecimal outrasDespesas,
                                    BigDecimal resultado, String observacao) {
    }

    public record RelatorioGeral(LocalDate inicio, LocalDate fim, BigDecimal faturamento, BigDecimal recebido,
                                 BigDecimal pendente, BigDecimal cancelado, BigDecimal pix, BigDecimal cartao,
                                 BigDecimal dinheiro, BigDecimal despesasPagas, BigDecimal despesasPendentes) {
    }

    public record MesCompras(YearMonth mes, BigDecimal total, BigDecimal kg, BigDecimal precoMedioPorKg) {
    }

    public record ComprasMensaisPeixe(int ano, List<MesCompras> meses) {
    }
}
