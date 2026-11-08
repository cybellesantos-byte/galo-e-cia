package br.com.pastelariass.comum;

/** Registro que não existe no banco. O TratadorDeErros transforma em HTTP 404. */
public class RecursoNaoEncontradoException extends RuntimeException {

    public RecursoNaoEncontradoException(String mensagem) {
        super(mensagem);
    }
}
