package br.com.pastelariass.comum;

/**
 * Os dois negócios da casa. Cada venda, produto, caixa e despesa pertence a um
 * deles, e as contas de um nunca se misturam com as do outro.
 *
 * Os valores são gravados como texto ("PASTELARIA"/"PEIXARIA"), e o CHECK da
 * migração repete essa lista. Mudou um, muda o outro.
 */
public enum Negocio {
    PASTELARIA,
    PEIXARIA
}
