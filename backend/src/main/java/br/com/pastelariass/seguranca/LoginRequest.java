package br.com.pastelariass.seguranca;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * O JSON que chega no login: {"username": "...", "senha": "..."}.
 *
 * record: classe só de dados, imutável, com construtor, getters, equals e
 * toString gerados pelo Java. Ideal para entrada e saída da API (DTO).
 *
 * @Size(max) limita o tamanho: impede que alguém envie um texto gigante
 * só para gastar processamento do BCrypt.
 */
public record LoginRequest(
        @NotBlank @Size(max = 60) String username,
        @NotBlank @Size(max = 128) String senha) {

    /** O toString padrão do record imprimiria a senha. Este esconde. */
    @Override
    public String toString() {
        return "LoginRequest[username=" + username + ", senha=***]";
    }
}
