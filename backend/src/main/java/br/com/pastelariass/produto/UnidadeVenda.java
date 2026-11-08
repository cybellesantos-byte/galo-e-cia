package br.com.pastelariass.produto;

/**
 * Como o produto é vendido. A pastelaria vende por UNIDADE (2 pastéis); a
 * peixaria vende por KG (1,350 kg de tilápia). Esta ligação é conferida no
 * ProdutoService.
 */
public enum UnidadeVenda {
    UNIDADE,
    KG
}
