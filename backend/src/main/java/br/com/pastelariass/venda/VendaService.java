package br.com.pastelariass.venda;

import br.com.pastelariass.auditoria.AcaoAuditoria;
import br.com.pastelariass.auditoria.AuditoriaService;
import br.com.pastelariass.caixa.Caixa;
import br.com.pastelariass.caixa.CaixaRepository;
import br.com.pastelariass.comum.Negocio;
import br.com.pastelariass.comum.RecursoNaoEncontradoException;
import br.com.pastelariass.comum.RegraDeNegocioException;
import br.com.pastelariass.comum.Validacoes;
import br.com.pastelariass.localidade.Localidade;
import br.com.pastelariass.localidade.LocalidadeRepository;
import br.com.pastelariass.produto.Produto;
import br.com.pastelariass.produto.ProdutoRepository;
import br.com.pastelariass.produto.UnidadeVenda;
import br.com.pastelariass.repasse.RepasseService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/**
 * Regras das vendas da pastelaria e da peixaria.
 *
 * Quem decide preço, taxa e total é o sistema, nunca quem envia o pedido. O
 * preço do produto e a taxa da localidade são copiados para a venda no momento
 * em que ela acontece.
 *
 * Diferença entre os negócios:
 *  - pastelaria: vende por unidade inteira e pode entregar (com taxa);
 *  - peixaria: vende por kg com até 3 casas e não entrega nesta versão.
 */
@Service
public class VendaService {

    static final int MAXIMO_ITENS = 50;
    static final int TAMANHO_MAXIMO_MOTIVO = 200;
    static final int TAMANHO_MAXIMO_OBSERVACAO = 500;

    private static final BigDecimal QUANTIDADE_MAXIMA = new BigDecimal("9999.999");

    private static final String JA_REGISTRADA = "Esta venda já foi registrada.";
    private static final ZoneId ZONA_DA_CASA = ZoneId.of("America/Sao_Paulo");

    /** Uma linha do pedido já conferida: o produto de verdade e a quantidade. */
    private record LinhaVenda(Produto produto, BigDecimal quantidade) {
        BigDecimal total() {
            return VendaItem.calcularTotal(produto.getPreco(), quantidade);
        }
    }

    private final VendaRepository vendaRepository;
    private final VendaItemRepository itemRepository;
    private final CaixaRepository caixaRepository;
    private final ProdutoRepository produtoRepository;
    private final LocalidadeRepository localidadeRepository;
    private final AuditoriaService auditoria;
    private final RepasseService repasseService;
    private final Clock relogio;

    public VendaService(VendaRepository vendaRepository, VendaItemRepository itemRepository,
                        CaixaRepository caixaRepository, ProdutoRepository produtoRepository,
                        LocalidadeRepository localidadeRepository, AuditoriaService auditoria,
                        RepasseService repasseService, Clock relogio) {
        this.vendaRepository = vendaRepository;
        this.itemRepository = itemRepository;
        this.caixaRepository = caixaRepository;
        this.produtoRepository = produtoRepository;
        this.localidadeRepository = localidadeRepository;
        this.auditoria = auditoria;
        this.repasseService = repasseService;
        this.relogio = relogio;
    }

    /**
     * Registra uma venda no caixa aberto do negócio informado.
     *
     * Se a MESMA chave já existe, devolve a venda que já estava gravada, sem
     * criar outra. É assim que um duplo clique ou uma rede instável não cobram
     * duas vezes.
     */
    @Transactional
    public Venda registrar(CriacaoVendaRequest dados) {
        var existente = vendaRepository.findByChaveDaVenda(dados.chaveDaVenda());
        if (existente.isPresent()) {
            return existente.get();
        }

        var negocio = dados.negocio();
        var caixa = caixaRepository.findByNegocioAndFechadoEmIsNull(negocio)
                .orElseThrow(() -> new RegraDeNegocioException(
                        "Abra o caixa da " + nomeDoNegocio(negocio) + " antes de registrar vendas."));
        // Trava o caixa: duas vendas em dinheiro ao mesmo tempo não leem o mesmo esperado.
        caixa = caixaRepository.buscarParaAlterar(caixa.getId()).orElseThrow();

        if (dados.origem() == null || dados.tipo() == null || dados.formaPagamento() == null) {
            throw new RegraDeNegocioException("Informe a origem, o tipo e a forma de pagamento da venda.");
        }
        var linhas = validarItens(negocio, dados.itens());
        var subtotal = linhas.stream().map(LinhaVenda::total).reduce(BigDecimal.ZERO, BigDecimal::add);
        var localidade = localidadeDaEntrega(negocio, dados.tipo(), dados.localidadeId());
        var taxaPadrao = localidade == null ? null : localidade.getTaxa();
        var taxa = taxaCobrada(dados.tipo(), taxaPadrao, dados.taxaEntrega());
        var total = subtotal.add(taxa);
        var pagamento = pagamento(dados.formaPagamento(), dados.valorRecebido(), total);

        var usuario = auditoria.quemEstaLogado();
        var venda = new Venda(dados.chaveDaVenda(), caixa.getId(), dados.origem(), dados.tipo(),
                localidade == null ? null : localidade.getId(), taxaPadrao, taxa, subtotal, dados.formaPagamento(),
                pagamento.recebido(), pagamento.troco(), observacao(dados.observacao()), agora(), usuario);
        try {
            vendaRepository.saveAndFlush(venda);
        } catch (DataIntegrityViolationException e) {
            // Mesma chave gravada por outro pedido ao mesmo tempo.
            throw new RegraDeNegocioException(JA_REGISTRADA);
        }
        for (var linha : linhas) {
            itemRepository.save(new VendaItem(venda.getId(), linha.produto(), linha.quantidade()));
        }
        if (dados.tipo() == TipoVenda.ENTREGA) {
            repasseService.gerar(venda.getId(), taxaPadrao, taxa, hoje());
        }
        auditoria.registrar(usuario, AcaoAuditoria.VENDA_REGISTRADA, "venda " + venda.getId(),
                "negocio=" + negocio + ", total=" + total + ", forma=" + dados.formaPagamento());
        return venda;
    }

    /** Confirma o recebimento de uma venda no Pix ou no cartão. Não mexe na gaveta. */
    @Transactional
    public Venda confirmarPagamento(Long id) {
        travarCaixaDaVenda(id);
        var venda = buscarPorId(id);
        if (venda.isCancelada()) {
            throw new RegraDeNegocioException("Venda cancelada não pode ter pagamento confirmado.");
        }
        if (venda.getFormaPagamento() == FormaPagamento.DINHEIRO) {
            throw new RegraDeNegocioException("Só vendas no Pix ou no cartão precisam de confirmação de pagamento.");
        }
        if (venda.getSituacaoPagamento() == SituacaoPagamento.PAGO) {
            throw new RegraDeNegocioException("O pagamento desta venda já foi confirmado.");
        }
        venda.confirmarPagamento();
        auditoria.registrar(auditoria.quemEstaLogado(), AcaoAuditoria.PAGAMENTO_CONFIRMADO,
                "venda " + id, "total=" + venda.getTotal());
        return venda;
    }

    /**
     * Cancela a venda, com motivo. Só é aceito enquanto o caixa dela estiver
     * aberto: caixa fechado não se altera.
     */
    @Transactional
    public Venda cancelar(Long id, String motivo) {
        var motivoLimpo = Validacoes.texto(motivo, TAMANHO_MAXIMO_MOTIVO, "O motivo");
        var caixa = travarCaixaDaVenda(id);
        var venda = buscarPorId(id);
        if (venda.isCancelada()) {
            throw new RegraDeNegocioException("Esta venda já foi cancelada.");
        }
        if (!caixa.estaAberto()) {
            throw new RegraDeNegocioException("Esta venda pertence a um caixa já fechado e não pode ser cancelada.");
        }
        repasseService.cancelarDaVenda(id);
        var usuario = auditoria.quemEstaLogado();
        venda.cancelar(motivoLimpo, usuario, agora());
        auditoria.registrar(usuario, AcaoAuditoria.VENDA_CANCELADA, "venda " + id, "motivo=" + motivoLimpo);
        return venda;
    }

    @Transactional(readOnly = true)
    public Venda buscarPorId(Long id) {
        return vendaRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Venda não encontrada."));
    }

    @Transactional(readOnly = true)
    public List<Venda> listarDoCaixa(Long caixaId) {
        return vendaRepository.findByCaixaIdOrderByCriadoEmAsc(caixaId);
    }

    @Transactional(readOnly = true)
    public List<VendaItem> itensDe(Long vendaId) {
        return itemRepository.findByVendaIdOrderByIdAsc(vendaId);
    }

    /**
     * Trava o caixa de uma venda antes de lê-la ou alterá-la. Cancelamento e
     * fechamento disputam a mesma trava, então um não passa por cima do outro.
     */
    private Caixa travarCaixaDaVenda(Long vendaId) {
        var caixaId = vendaRepository.buscarCaixaId(vendaId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Venda não encontrada."));
        return caixaRepository.buscarParaAlterar(caixaId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Caixa não encontrado."));
    }

    /** Confere cada linha do pedido e devolve os produtos reais. Preço NUNCA vem do pedido. */
    private List<LinhaVenda> validarItens(Negocio negocio, List<ItemVendaRequest> itens) {
        if (itens == null || itens.isEmpty()) {
            throw new RegraDeNegocioException("A venda precisa de pelo menos um item.");
        }
        if (itens.size() > MAXIMO_ITENS) {
            throw new RegraDeNegocioException("Uma venda aceita no máximo " + MAXIMO_ITENS + " itens.");
        }
        var produtosVistos = new HashSet<Long>();
        var linhas = new ArrayList<LinhaVenda>();
        for (var item : itens) {
            if (!produtosVistos.add(item.produtoId())) {
                throw new RegraDeNegocioException("Cada produto deve aparecer uma vez só na venda. Some as quantidades.");
            }
            var produto = produtoRepository.findById(item.produtoId())
                    .orElseThrow(() -> new RecursoNaoEncontradoException("Produto não encontrado."));
            if (produto.getNegocio() != negocio) {
                throw new RegraDeNegocioException("O produto " + produto.getNome() + " não é da "
                        + nomeDoNegocio(negocio) + ".");
            }
            if (!produto.isAtivo()) {
                throw new RegraDeNegocioException("O produto " + produto.getNome() + " não está na lista de venda.");
            }
            if (!produto.isDisponivel()) {
                throw new RegraDeNegocioException("O produto " + produto.getNome() + " está esgotado.");
            }
            validarQuantidade(produto, item.quantidade());
            linhas.add(new LinhaVenda(produto, item.quantidade()));
        }
        return linhas;
    }

    /**
     * Produto por unidade aceita só inteiro (4 pastéis). Produto por kg aceita
     * até 3 casas (1,350 kg).
     */
    private static void validarQuantidade(Produto produto, BigDecimal quantidade) {
        if (quantidade.signum() <= 0) {
            throw new RegraDeNegocioException("A quantidade deve ser maior que zero.");
        }
        if (produto.getUnidadeVenda() == UnidadeVenda.UNIDADE && quantidade.stripTrailingZeros().scale() > 0) {
            throw new RegraDeNegocioException("O produto " + produto.getNome()
                    + " é vendido por unidade: use quantidades inteiras.");
        }
        if (quantidade.stripTrailingZeros().scale() > 3) {
            throw new RegraDeNegocioException("A quantidade aceita no máximo 3 casas decimais.");
        }
        if (quantidade.compareTo(QUANTIDADE_MAXIMA) > 0) {
            throw new RegraDeNegocioException("A quantidade é alta demais.");
        }
    }

    /**
     * Retirada não tem localidade. Entrega precisa de uma localidade ativa, e
     * só a pastelaria entrega nesta versão.
     */
    private Localidade localidadeDaEntrega(Negocio negocio, TipoVenda tipo, Long localidadeId) {
        if (tipo == TipoVenda.RETIRADA) {
            if (localidadeId != null) {
                throw new RegraDeNegocioException("Venda para retirada não tem localidade de entrega.");
            }
            return null;
        }
        if (negocio == Negocio.PEIXARIA) {
            throw new RegraDeNegocioException("A peixaria não tem entrega nesta versão.");
        }
        if (localidadeId == null) {
            throw new RegraDeNegocioException("Informe a localidade da entrega.");
        }
        var localidade = localidadeRepository.findById(localidadeId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Localidade não encontrada."));
        if (!localidade.isAtivo()) {
            throw new RegraDeNegocioException("A localidade " + localidade.getNome() + " não é mais atendida.");
        }
        return localidade;
    }

    /**
     * Pix e cartão: nada a receber agora, ficam pendentes. Dinheiro: o valor
     * recebido precisa cobrir o total, e a diferença é o troco.
     */
    private static Pagamento pagamento(FormaPagamento forma, BigDecimal valorRecebido, BigDecimal total) {
        if (forma != FormaPagamento.DINHEIRO) {
            if (valorRecebido != null) {
                throw new RegraDeNegocioException("Venda no Pix ou no cartão não informa valor recebido.");
            }
            return new Pagamento(null, null);
        }
        var recebido = Validacoes.valorMonetario(valorRecebido, "O valor recebido");
        if (recebido.compareTo(total) < 0) {
            throw new RegraDeNegocioException("O valor recebido é menor que o total da venda.");
        }
        return new Pagamento(recebido, recebido.subtract(total));
    }

    private record Pagamento(BigDecimal recebido, BigDecimal troco) {
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

    /**
     * A taxa que o cliente paga. Sem taxa informada, é a padrão da localidade.
     * Retirada não tem taxa, então não aceita valor informado.
     */
    private static BigDecimal taxaCobrada(TipoVenda tipo, BigDecimal taxaPadrao, BigDecimal taxaInformada) {
        if (tipo == TipoVenda.RETIRADA) {
            if (taxaInformada != null) {
                throw new RegraDeNegocioException("Venda para retirada não tem taxa de entrega.");
            }
            return BigDecimal.ZERO;
        }
        if (taxaInformada == null) {
            return taxaPadrao;
        }
        return Validacoes.valorMonetario(taxaInformada, "A taxa de entrega");
    }

    private LocalDate hoje() {
        return LocalDate.now(relogio.withZone(ZONA_DA_CASA));
    }

    private Instant agora() {
        return Instant.now(relogio).truncatedTo(ChronoUnit.MICROS);
    }
}
