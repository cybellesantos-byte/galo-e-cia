package br.com.pastelariass.usuario;

import java.time.Instant;

/**
 * Como um usuário aparece para o ADMIN na listagem e no cadastro.
 * Nunca inclui o hash da senha.
 */
public record UsuarioResponse(
        Long id,
        String username,
        String nome,
        Papel papel,
        boolean ativo,
        Instant criadoEm,
        Instant ultimoLoginEm) {

    static UsuarioResponse de(Usuario usuario) {
        return new UsuarioResponse(usuario.getId(), usuario.getUsername(), usuario.getNome(),
                usuario.getPapel(), usuario.isAtivo(), usuario.getCriadoEm(), usuario.getUltimoLoginEm());
    }
}
