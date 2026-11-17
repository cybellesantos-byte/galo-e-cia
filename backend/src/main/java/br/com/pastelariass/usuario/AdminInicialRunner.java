package br.com.pastelariass.usuario;

import br.com.pastelariass.comum.RegraDeNegocioException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Cria o primeiro administrador quando o sistema sobe, a partir de
 * ADMIN_USERNAME e ADMIN_PASSWORD (vindas do .env).
 *
 * Problema que resolve: sem nenhum usuário no banco, ninguém consegue fazer
 * login para cadastrar o primeiro usuário.
 *
 * Regras:
 *  - Se o usuário já existe, não faz NADA. Nunca sobrescreve a senha: trocar
 *    o .env depois não muda a senha de quem já existe.
 *  - O cadastro passa pelo UsuarioService, com as MESMAS regras do cadastro
 *    pela API (formato do nome, senha mínima...).
 *  - Se alguma regra falhar, o sistema NÃO sobe. É melhor falhar cedo do que
 *    criar um administrador com senha fraca.
 */
@Component
public class AdminInicialRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminInicialRunner.class);

    private final UsuarioService usuarioService;
    private final String username;
    private final String senha;

    public AdminInicialRunner(UsuarioService usuarioService,
                              @Value("${app.admin.username}") String username,
                              @Value("${app.admin.password}") String senha) {
        this.usuarioService = usuarioService;
        this.username = username;
        this.senha = senha;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (usuarioService.existe(username)) {
            log.info("Administrador inicial '{}' já existe. Nada a fazer.", username);
            return;
        }
        try {
            usuarioService.cadastrar(new CadastroUsuarioRequest(username, "Administrador", senha, Papel.ADMIN));
        } catch (RegraDeNegocioException e) {
            throw new IllegalStateException("Administrador inicial não foi criado: " + e.getMessage()
                    + " Confira ADMIN_USERNAME e ADMIN_PASSWORD no .env.", e);
        }
        // Só o nome vai para o log. A senha (nem o hash) nunca é registrada.
        log.info("Administrador inicial '{}' criado.", username);
    }
}
