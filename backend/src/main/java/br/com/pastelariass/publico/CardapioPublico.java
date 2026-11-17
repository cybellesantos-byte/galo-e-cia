package br.com.pastelariass.publico;

import java.math.BigDecimal;
import java.util.List;

/**
 * TUDO o que o cardápio público mostra a quem não fez login. Este arquivo é a
 * lista completa: se um dado não está aqui, ele não sai do sistema.
 *
 * Por isso os campos são escolhidos um a um, à mão. A resposta nunca reaproveita
 * ProdutoResponse (que tem custo e id): adicionar um campo ali, por distração,
 * vazaria o custo para a internet.
 *
 * Fora, de propósito: custo, margem, id interno, se o produto está "ativo",
 * vendas, caixa, usuários, quem alterou e quando.
 */
public record CardapioPublico(
        /** A equipe liga e desliga. A página usa para dizer "estamos abertos" ou "fechados". */
        boolean aberta,
        /** Só dígitos com o 55 (5583999998888), ou null se ainda não foi informado. */
        String whatsapp,
        List<Item> itens,
        List<LocalDeEntrega> locais) {

    /** Um produto do cardápio. Esgotado continua na lista, para o cliente ver que existe. */
    public record Item(String nome, String descricao, String categoria, BigDecimal preco, boolean esgotado) {
    }

    /** Um lugar onde a pastelaria entrega, com a taxa. */
    public record LocalDeEntrega(String nome, BigDecimal taxa) {
    }
}
