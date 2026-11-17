package br.com.pastelariass.seguranca;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * O JSON para trocar a própria senha:
 * {"senhaAtual": "...", "senhaNova": "..."}
 *
 * As regras da senha nova (tamanho mínimo, etc.) ficam no UsuarioService.
 */
public record TrocaSenhaRequest(
        @NotBlank @Size(max = 128) String senhaAtual,
        @NotBlank @Size(max = 128) String senhaNova) {

    @Override
    public String toString() {
        return "TrocaSenhaRequest[senhaAtual=***, senhaNova=***]";
    }
}
