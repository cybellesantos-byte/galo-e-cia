package br.com.pastelariass.usuario;

import br.com.pastelariass.comum.RecursoNaoEncontradoException;
import br.com.pastelariass.auditoria.AcaoAuditoria;
import br.com.pastelariass.auditoria.AuditoriaService;
import br.com.pastelariass.comum.RegraDeNegocioException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Regras de negócio de usuários.
 *
 * Todo cadastro de usuário passa por aqui, venha da API ou do administrador
 * inicial. Assim, as regras valem para todo mundo, sem exceção.
 */
@Service
public class UsuarioService {

    public static final int TAMANHO_MINIMO_SENHA = 12;
    static final int TAMANHO_MAXIMO_SENHA = 128;
    static final int TAMANHO_MAXIMO_NOME = 120;

    /** 3 a 60 caracteres: letras sem acento, números, ponto, hífen ou sublinhado. Sem espaços. */
    private static final Pattern FORMATO_USERNAME = Pattern.compile("[a-zA-Z0-9._-]{3,60}");

    private static final String USERNAME_EM_USO = "Já existe um usuário com esse nome de usuário.";

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditoriaService auditoria;
    private final Clock relogio;

    public UsuarioService(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder,
                          AuditoriaService auditoria, Clock relogio) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditoria = auditoria;
        this.relogio = relogio;
    }

    /**
     * Cadastra um usuário novo, conferindo todas as regras ANTES de gravar.
     * A senha é transformada em hash aqui; a senha pura nunca sai deste método.
     *
     * Recebe um objeto em vez de valores soltos: com três String seguidas
     * (username, nome, senha), seria fácil passar na ordem errada sem o Java
     * reclamar. Com dados.username(), cada valor tem nome.
     *
     * As regras são conferidas aqui mesmo que a API já valide o formato: o
     * AdminInicialRunner também chama este método, sem passar pela API.
     */
    @Transactional
    public Usuario cadastrar(CadastroUsuarioRequest dados) {
        var usernameLimpo = dados.username() == null ? "" : dados.username().strip();
        var nomeLimpo = dados.nome() == null ? "" : dados.nome().strip();
        var senha = dados.senha();
        var papel = dados.papel();

        if (!FORMATO_USERNAME.matcher(usernameLimpo).matches()) {
            throw new RegraDeNegocioException("O nome de usuário deve ter de 3 a 60 caracteres, "
                    + "só com letras sem acento, números, ponto, hífen ou sublinhado (sem espaços).");
        }
        if (nomeLimpo.isEmpty() || nomeLimpo.length() > TAMANHO_MAXIMO_NOME) {
            throw new RegraDeNegocioException("O nome deve ter de 1 a " + TAMANHO_MAXIMO_NOME + " caracteres.");
        }
        validarSenha(senha);
        if (papel == null) {
            throw new RegraDeNegocioException("Informe o papel: ADMIN ou EQUIPE.");
        }
        if (usuarioRepository.existsByUsernameIgnoreCase(usernameLimpo)) {
            throw new RegraDeNegocioException(USERNAME_EM_USO);
        }

        var usuario = new Usuario(usernameLimpo, nomeLimpo, passwordEncoder.encode(senha), papel);
        try {
            // saveAndFlush: grava AGORA. Se duas pessoas cadastrarem o mesmo nome ao
            // mesmo tempo, as duas passam pelo "exists" acima, mas só uma passa pelo
            // índice único do banco. A outra cai aqui, com uma mensagem clara.
            var salvo = usuarioRepository.saveAndFlush(usuario);
            auditoria.registrar(auditoria.quemEstaLogado(), AcaoAuditoria.USUARIO_CADASTRADO,
                    salvo.getUsername(), "papel=" + papel);
            return salvo;
        } catch (DataIntegrityViolationException e) {
            throw new RegraDeNegocioException(USERNAME_EM_USO);
        }
    }

    /**
     * Desativa um usuário. Não apaga: a ficha continua, e a pessoa só deixa de entrar.
     *
     * Duas proteções, para o sistema nunca ficar sem quem o administre:
     *  - ninguém desativa a própria conta;
     *  - não dá para desativar o último ADMIN ativo.
     */
    @Transactional
    public Usuario desativar(Long id, String solicitante) {
        var alvo = buscarPorId(id);

        if (alvo.getUsername().equalsIgnoreCase(solicitante)) {
            throw new RegraDeNegocioException("Você não pode desativar a própria conta.");
        }
        boolean ultimoAdminAtivo = alvo.getPapel() == Papel.ADMIN
                && alvo.isAtivo()
                && usuarioRepository.countByPapelAndAtivoTrue(Papel.ADMIN) <= 1;
        if (ultimoAdminAtivo) {
            throw new RegraDeNegocioException("Não é possível desativar o último administrador ativo.");
        }

        alvo.alterarAtivo(false);
        auditoria.registrar(solicitante, AcaoAuditoria.USUARIO_DESATIVADO, alvo.getUsername(), null);
        return alvo;
    }

    @Transactional
    public Usuario reativar(Long id) {
        var alvo = buscarPorId(id);
        alvo.alterarAtivo(true);
        auditoria.registrar(auditoria.quemEstaLogado(), AcaoAuditoria.USUARIO_REATIVADO, alvo.getUsername(), null);
        return alvo;
    }

    /**
     * Troca a senha de quem está logado, exigindo a senha atual.
     * Exigir a atual impede que alguém com a sessão aberta num computador
     * alheio troque a senha sem saber a antiga.
     */
    @Transactional
    public Instant trocarSenha(String username, String senhaAtual, String senhaNova) {
        var usuario = buscarPorUsername(username);
        if (senhaAtual == null || !passwordEncoder.matches(senhaAtual, usuario.getSenhaHash())) {
            throw new RegraDeNegocioException("A senha atual está incorreta.");
        }
        validarSenha(senhaNova);
        var quando = agora();
        usuario.trocarSenhaHash(passwordEncoder.encode(senhaNova), quando);
        auditoria.registrar(username, AcaoAuditoria.SENHA_TROCADA, usuario.getUsername(), null);
        // Devolve a hora da troca: quem chamou usa para manter a PRÓPRIA sessão viva.
        return quando;
    }

    /**
     * O filtro de segurança chama isto a cada requisição. Responde true quando a
     * sessão deve cair: o usuário foi desativado, ou trocou a senha depois do
     * login dessa sessão. Usuário que não existe no banco não derruba a sessão
     * aqui: toda sessão real nasce de um login, e nunca apagamos usuários.
     */
    @Transactional(readOnly = true)
    public boolean sessaoDeveSerEncerrada(String username, Instant autenticadoEm) {
        return usuarioRepository.findByUsernameIgnoreCase(username)
                .map(usuario -> !usuario.isAtivo()
                        || (autenticadoEm != null && usuario.senhaFoiTrocadaApos(autenticadoEm)))
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public Usuario buscarPorId(Long id) {
        return usuarioRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Usuário não encontrado."));
    }

    /**
     * "Agora", na precisão do banco (microssegundos). O Java guarda nanossegundos
     * e o PostgreSQL arredonda para microssegundos; sem este corte, a data que
     * volta do banco poderia ser um pouco MAIOR que a guardada na sessão, e a
     * própria pessoa seria derrubada logo após trocar a senha.
     */
    private Instant agora() {
        return Instant.now(relogio).truncatedTo(ChronoUnit.MICROS);
    }

    /** A única regra de senha do sistema: usada no cadastro e na troca. */
    private static void validarSenha(String senha) {
        if (senha == null || senha.length() < TAMANHO_MINIMO_SENHA || senha.length() > TAMANHO_MAXIMO_SENHA) {
            throw new RegraDeNegocioException("A senha deve ter de " + TAMANHO_MINIMO_SENHA
                    + " a " + TAMANHO_MAXIMO_SENHA + " caracteres.");
        }
    }

    @Transactional(readOnly = true)
    public boolean existe(String username) {
        return usuarioRepository.existsByUsernameIgnoreCase(username);
    }

    @Transactional(readOnly = true)
    public List<Usuario> listar() {
        return usuarioRepository.findAll(Sort.by("nome"));
    }

    /**
     * Registra o horário do login e devolve o usuário.
     * @Transactional: a alteração em ultimoLoginEm é gravada no banco ao fim
     * do método, sem precisar chamar save() (o JPA detecta a mudança sozinho).
     */
    @Transactional
    public Usuario registrarLogin(String username) {
        var usuario = buscarPorUsername(username);
        usuario.registrarLogin(agora());
        return usuario;
    }

    @Transactional(readOnly = true)
    public Usuario buscarPorUsername(String username) {
        return usuarioRepository.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Usuário não encontrado."));
    }
}
