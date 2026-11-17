package br.com.pastelariass.caixa;

import java.math.BigDecimal;

/**
 * A conta aberta do "esperado na gaveta", parcela por parcela.
 *
 * Serve para quem fecha o caixa entender DE ONDE vem o número esperado,
 * e achar a causa quando a contagem não bate.
 *
 *   saldo inicial + reforços − sangrias + vendas em dinheiro − despesas − repasses
 *
 * Só entra o que passa pela gaveta: vendas no Pix, vendas canceladas e
 * despesas pagas por outro meio ficam de fora.
 */
public record ComposicaoEsperado(
        BigDecimal saldoInicial,
        BigDecimal reforcos,
        BigDecimal sangrias,
        BigDecimal vendasDinheiro,
        BigDecimal despesas,
        BigDecimal repasses) {

    /** O total: a única fórmula do esperado no sistema inteiro. */
    public BigDecimal esperado() {
        return saldoInicial
                .add(reforcos)
                .subtract(sangrias)
                .add(vendasDinheiro)
                .subtract(despesas)
                .subtract(repasses);
    }
}
