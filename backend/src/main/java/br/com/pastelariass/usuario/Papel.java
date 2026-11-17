package br.com.pastelariass.usuario;

/**
 * Papéis de acesso à área administrativa.
 *
 * ADMIN: pode tudo, inclusive operações sensíveis (devoluções, repasses, usuários).
 * EQUIPE: operação do dia a dia (pedidos, caixa, cardápio).
 *
 * Os valores são gravados no banco como texto ("ADMIN"/"EQUIPE"), e o CHECK
 * da migração V1 repete essa lista. Se um dia criarmos um papel novo, o enum
 * E a migração precisam mudar juntos.
 */
public enum Papel {
    ADMIN,
    EQUIPE
}
