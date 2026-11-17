package br.com.pastelariass.localidade;

import br.com.pastelariass.auditoria.AcaoAuditoria;
import br.com.pastelariass.auditoria.AuditoriaService;
import br.com.pastelariass.comum.RecursoNaoEncontradoException;
import br.com.pastelariass.comum.RegraDeNegocioException;
import br.com.pastelariass.comum.Validacoes;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * Regras das localidades de entrega. Só a pastelaria entrega, então não há
 * negócio aqui: a localidade e a taxa valem para a entrega da pastelaria.
 */
@Service
public class LocalidadeService {

    static final int TAMANHO_MAXIMO_NOME = 120;

    private static final String NOME_EM_USO = "Já existe uma localidade com esse nome.";

    private final LocalidadeRepository localidadeRepository;
    private final AuditoriaService auditoria;

    public LocalidadeService(LocalidadeRepository localidadeRepository, AuditoriaService auditoria) {
        this.localidadeRepository = localidadeRepository;
        this.auditoria = auditoria;
    }

    @Transactional
    public Localidade cadastrar(String nome, BigDecimal taxa) {
        var nomeLimpo = Validacoes.texto(nome, TAMANHO_MAXIMO_NOME, "O nome");
        var taxaValida = Validacoes.valorMonetario(taxa, "A taxa");
        conferirNomeLivre(nomeLimpo, null);

        return gravar(new Localidade(nomeLimpo, taxaValida));
    }

    @Transactional
    public Localidade alterar(Long id, String nome, BigDecimal taxa, boolean ativo) {
        var localidade = buscarPorId(id);
        var nomeLimpo = Validacoes.texto(nome, TAMANHO_MAXIMO_NOME, "O nome");
        var taxaValida = Validacoes.valorMonetario(taxa, "A taxa");
        conferirNomeLivre(nomeLimpo, id);

        var taxaAntes = localidade.getTaxa();
        localidade.alterarDados(nomeLimpo, taxaValida, ativo);
        var salva = gravar(localidade);
        // A taxa é dinheiro de entrega: mudar fica registrado com o valor de antes.
        auditoria.registrar(auditoria.quemEstaLogado(), AcaoAuditoria.LOCALIDADE_ALTERADA, nomeLimpo,
                "taxa=" + taxaAntes + "->" + taxaValida + ", ativo=" + ativo);
        return salva;
    }

    @Transactional(readOnly = true)
    public List<Localidade> listar() {
        return localidadeRepository.findAll(Sort.by("nome"));
    }

    @Transactional(readOnly = true)
    public Localidade buscarPorId(Long id) {
        return localidadeRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Localidade não encontrada."));
    }

    /** Mesma ideia do ProdutoService: se a corrida passar pela conferência, o índice único segura. */
    private Localidade gravar(Localidade localidade) {
        try {
            return localidadeRepository.saveAndFlush(localidade);
        } catch (DataIntegrityViolationException e) {
            throw new RegraDeNegocioException(NOME_EM_USO);
        }
    }

    private void conferirNomeLivre(String nome, Long idDaLocalidade) {
        boolean emUso = idDaLocalidade == null
                ? localidadeRepository.existsByNomeIgnoreCase(nome)
                : localidadeRepository.existsByNomeIgnoreCaseAndIdNot(nome, idDaLocalidade);
        if (emUso) {
            throw new RegraDeNegocioException(NOME_EM_USO);
        }
    }
}
