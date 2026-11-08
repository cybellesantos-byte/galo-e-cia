package br.com.pastelariass.seguranca;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.context.SecurityContextRepository;
import br.com.pastelariass.publico.LimiteRequisicoesPublicas;
import br.com.pastelariass.publico.LimiteRequisicoesPublicasFilter;
import br.com.pastelariass.usuario.UsuarioService;

import java.io.IOException;

/**
 * Regras de segurança do sistema.
 *
 * SESSÃO: o login cria uma sessão no servidor. O navegador guarda só o cookie
 * JSESSIONID, marcado como HttpOnly (o JavaScript não consegue lê-lo).
 *
 * CSRF: como o navegador envia o cookie de sessão sozinho em toda requisição,
 * um site malicioso poderia disparar ações em nome de quem está logado. Para
 * impedir, toda requisição que ALTERA dados (POST, PUT, PATCH, DELETE) precisa
 * de um token CSRF: o Spring o grava no cookie XSRF-TOKEN e a página o devolve
 * no header X-XSRF-TOKEN. Um site de fora não consegue ler esse cookie.
 *
 * ACESSO: tudo exige login, exceto buscar o token CSRF, fazer o login, a tela (arquivos
 * estáticos) e o cardápio público (somente leitura).
 */
@Configuration
// Liga o @PreAuthorize: permissões declaradas em cima de cada controller/método.
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           SecurityContextRepository securityContextRepository,
                                           UsuarioService usuarioService,
                                           LimiteRequisicoesPublicas limitePublico) throws Exception {
        http
                .csrf(csrf -> csrf.spa())
                .securityContext(ctx -> ctx.securityContextRepository(securityContextRepository))
                // O fiscal da fila vem primeiro: um robô é barrado antes de gastar qualquer outra coisa.
                .addFilterBefore(new LimiteRequisicoesPublicasFilter(limitePublico), SecurityContextHolderFilter.class)
                // Logo depois de carregar quem está logado, confere se ainda pode entrar.
                .addFilterAfter(new VerificaUsuarioAtivoFilter(usuarioService), SecurityContextHolderFilter.class)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/api/auth/csrf").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                        // "/error" é para onde o Spring encaminha erros internos.
                        // Sem liberar, um erro viraria um 401 enganoso.
                        .requestMatchers("/error").permitAll()
                        // A ÚNICA porta pública de dados: o cardápio, só leitura. Endereço exato, sem
                        // curinga: uma rota nova em /api/publico/ continua trancada até alguém liberar.
                        .requestMatchers(HttpMethod.GET, "/api/publico/cardapio").permitAll()
                        // A tela (página e arquivos estáticos) é pública: só os dados passam pelo login.
                        // Sem dados sensíveis nos arquivos, então liberar a casca não expõe nada.
                        // "/img/**" tem as logos: o login precisa delas antes de qualquer sessão.
                        .requestMatchers(HttpMethod.GET, "/", "/index.html", "/favicon.ico",
                                "/css/**", "/js/**", "/vendor/**", "/img/**").permitAll()
                        // A página do cliente (o cardápio). Só a casca; os dados vêm da porta pública abaixo.
                        .requestMatchers(HttpMethod.GET, "/cardapio", "/cardapio.html").permitAll()
                        .anyRequest().authenticated())
                .logout(logout -> logout
                        .logoutUrl("/api/auth/logout")
                        .logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT)))
                .exceptionHandling(ex -> ex
                        // Sem login → 401. Logado, mas sem permissão (ou sem CSRF) → 403.
                        .authenticationEntryPoint((req, res, e) ->
                                escreverErro(res, HttpStatus.UNAUTHORIZED, "Faça login para continuar."))
                        .accessDeniedHandler((req, res, e) ->
                                escreverErro(res, HttpStatus.FORBIDDEN, "Acesso negado.")))
                // Desliga o formulário HTML padrão e o popup de usuário/senha do
                // navegador: o login é feito pela nossa API em JSON.
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable());

        return http.build();
    }

    /**
     * BCrypt: algoritmo lento de propósito e com "sal" aleatório embutido.
     * Lento = adivinhar senhas por tentativa e erro fica caro para um atacante.
     * Sal = duas pessoas com a mesma senha têm hashes diferentes.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Quem confere usuário e senha no login: busca o usuário com o
     * UsuarioDetailsService e compara a senha digitada com o hash usando o BCrypt.
     */
    @Bean
    public AuthenticationManager authenticationManager(UsuarioDetailsService usuarioDetailsService,
                                                       PasswordEncoder passwordEncoder) {
        var provider = new DaoAuthenticationProvider(usuarioDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(provider);
    }

    /** Guarda o usuário autenticado na sessão HTTP, entre uma requisição e outra. */
    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    private static void escreverErro(HttpServletResponse res, HttpStatus status, String mensagem) throws IOException {
        res.setStatus(status.value());
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        res.setCharacterEncoding("UTF-8");
        // Mensagens fixas, sem aspas nem dados do usuário: seguro montar o JSON à mão.
        res.getWriter().write("{\"mensagem\":\"" + mensagem + "\",\"detalhes\":[]}");
    }
}
