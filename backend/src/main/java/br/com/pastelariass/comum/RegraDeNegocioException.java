package br.com.pastelariass.comum;

/**
 * Uma regra do negócio foi violada: "já existe um usuário com esse nome",
 * "senha curta demais", "produto esgotado"...
 *
 * O TratadorDeErros transforma em HTTP 422 (Unprocessable Content): os dados
 * chegaram no formato certo, mas o pedido não pode ser atendido.
 */
public class RegraDeNegocioException extends RuntimeException {

    public RegraDeNegocioException(String mensagem) {
        super(mensagem);
    }
}
