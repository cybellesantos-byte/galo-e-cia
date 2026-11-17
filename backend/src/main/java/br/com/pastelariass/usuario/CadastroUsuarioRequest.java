package br.com.pastelariass.usuario;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * O JSON que o ADMIN envia para cadastrar alguém:
 * {"username": "ana", "nome": "Ana Souza", "senha": "...", "papel": "EQUIPE"}
 *
 * Aqui só conferimos o FORMATO básico (preenchido, tamanho máximo).
 * As regras de negócio (sem espaços, senha mínima, nome repetido) ficam no
 * UsuarioService, porque valem também para quem não passa por esta API.
 *
 * Este mesmo objeto é a entrada do UsuarioService.cadastrar(...). Atenção:
 * as anotações abaixo só são conferidas quando o objeto chega pela API (@Valid).
 * Criado com "new" no código (como no AdminInicialRunner), ninguém as confere,
 * e é por isso que o Service repete as verificações importantes.
 */
public record CadastroUsuarioRequest(
        @NotBlank @Size(max = 60) String username,
        @NotBlank @Size(max = 120) String nome,
        @NotBlank @Size(max = 128) String senha,
        @NotNull Papel papel) {

    @Override
    public String toString() {
        return "CadastroUsuarioRequest[username=" + username + ", nome=" + nome
                + ", senha=***, papel=" + papel + "]";
    }
}
