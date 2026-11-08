package br.com.pastelariass.seguranca;

import br.com.pastelariass.usuario.Papel;
import br.com.pastelariass.usuario.Usuario;

/**
 * O que a API devolve sobre o usuário logado.
 *
 * Por que não devolver a entidade Usuario direto? Porque ela tem senhaHash.
 * O DTO escolhe EXATAMENTE o que sai: nada de hash, id interno ou datas.
 */
public record UsuarioLogadoResponse(String username, String nome, Papel papel) {

    static UsuarioLogadoResponse de(Usuario usuario) {
        return new UsuarioLogadoResponse(usuario.getUsername(), usuario.getNome(), usuario.getPapel());
    }
}
