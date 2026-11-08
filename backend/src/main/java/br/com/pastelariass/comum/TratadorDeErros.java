package br.com.pastelariass.comum;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

/**
 * Transforma exceções em respostas JSON, sempre no mesmo formato:
 *   { "mensagem": "...", "detalhes": [ ... ] }
 *
 * Por que centralizar? Sem isto, cada erro sairia num formato diferente, e
 * erros inesperados mostrariam detalhes internos (classes, SQL) ao navegador.
 * Quem chama a API sempre sabe onde procurar a mensagem.
 */
@RestControllerAdvice
public class TratadorDeErros {

    private static final Logger log = LoggerFactory.getLogger(TratadorDeErros.class);

    public record ErroResponse(String mensagem, List<String> detalhes) {
        public static ErroResponse de(String mensagem) {
            return new ErroResponse(mensagem, List.of());
        }
    }

    /** 400: campos que não passaram no Bean Validation (@NotBlank, @Size...). */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErroResponse> dadosInvalidos(MethodArgumentNotValidException e) {
        var detalhes = e.getBindingResult().getAllErrors().stream()
                .map(erro -> erro instanceof FieldError campo
                        ? campo.getField() + ": " + campo.getDefaultMessage()
                        : erro.getDefaultMessage())
                .sorted()
                .toList();
        return ResponseEntity.badRequest().body(new ErroResponse("Dados inválidos.", detalhes));
    }

    /**
     * 400: parâmetro de URL com valor que não existe ou em formato errado
     * (ex.: ?negocio=XPTO, ou uma data escrita de outro jeito).
     * Sem isto, o erro cairia no tratador genérico e viraria 500.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErroResponse> parametroInvalido(MethodArgumentTypeMismatchException e) {
        return ResponseEntity.badRequest()
                .body(ErroResponse.de("Valor inválido para " + e.getName() + "."));
    }

    /** 400: JSON malformado ou ausente. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErroResponse> corpoIlegivel(HttpMessageNotReadableException e) {
        return ResponseEntity.badRequest().body(ErroResponse.de("Corpo da requisição inválido."));
    }

    /** 422: uma regra do negócio foi violada. A mensagem explica qual. */
    @ExceptionHandler(RegraDeNegocioException.class)
    public ResponseEntity<ErroResponse> regraDeNegocio(RegraDeNegocioException e) {
        return ResponseEntity.status(422).body(ErroResponse.de(e.getMessage()));
    }

    /**
     * 403: logado, mas sem permissão (ex.: EQUIPE chamando um método só de ADMIN).
     *
     * Sem este método, o "acesso negado" do @PreAuthorize cairia no tratador
     * genérico lá embaixo e viraria um 500 (erro interno), escondendo o motivo real.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErroResponse> acessoNegado(AccessDeniedException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ErroResponse.de("Acesso negado."));
    }

    /** 404: registro que não existe. */
    @ExceptionHandler(RecursoNaoEncontradoException.class)
    public ResponseEntity<ErroResponse> naoEncontrado(RecursoNaoEncontradoException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ErroResponse.de(e.getMessage()));
    }

    /**
     * Qualquer outra exceção.
     *
     * Erros "normais" do Spring MVC (rota inexistente → 404, método errado → 405)
     * já sabem o próprio status: eles implementam ErrorResponse. Mantemos esse
     * status. Só o que é realmente inesperado vira 500, e o detalhe vai para o
     * log do servidor, nunca para a resposta.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErroResponse> inesperado(Exception e) {
        if (e instanceof ErrorResponse erroDoSpring) {
            var status = HttpStatus.valueOf(erroDoSpring.getStatusCode().value());
            var mensagem = status == HttpStatus.NOT_FOUND ? "Recurso não encontrado." : status.getReasonPhrase();
            return ResponseEntity.status(status).body(ErroResponse.de(mensagem));
        }
        log.error("Erro inesperado", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErroResponse.de("Erro interno. Tente novamente."));
    }
}
