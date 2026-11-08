package br.com.pastelariass.comum;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Regras de formato compartilhadas pelos módulos. Ficam num lugar só para que
 * produtos, localidades e as próximas etapas digam as mesmas coisas com as
 * mesmas palavras.
 *
 * Estas regras valem mesmo quando o objeto não vem pela API (sem @Valid).
 */
public final class Validacoes {

    /** Maior valor que cabe em NUMERIC(10,2), como o banco guarda: 99.999.999,99. */
    private static final BigDecimal VALOR_MAXIMO = new BigDecimal("99999999.99");

    private Validacoes() {
    }

    /** Texto obrigatório, sem espaços nas pontas, dentro do tamanho máximo. */
    public static String texto(String valor, int maximo, String campo) {
        var limpo = valor == null ? "" : valor.strip();
        if (limpo.isEmpty() || limpo.length() > maximo) {
            throw new RegraDeNegocioException(campo + " deve ter de 1 a " + maximo + " caracteres.");
        }
        return limpo;
    }

    /**
     * Texto que pode ficar em branco (ex.: a descrição de um produto). Vazio ou só
     * espaços vira null; passou do tamanho, recusa em vez de cortar em silêncio.
     */
    public static String textoOpcional(String valor, int maximo, String campo) {
        var limpo = valor == null ? "" : valor.strip();
        if (limpo.isEmpty()) {
            return null;
        }
        if (limpo.length() > maximo) {
            throw new RegraDeNegocioException(campo + " deve ter no máximo " + maximo + " caracteres.");
        }
        return limpo;
    }

    /**
     * Arredonda para centavos com "metade para cima" (HALF_UP). É a regra do
     * plano para venda por peso: 1,333 kg × R$ 28,00 = R$ 37,324 → R$ 37,32.
     */
    public static BigDecimal arredondarCentavos(BigDecimal valor) {
        return valor.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Valor em reais: obrigatório, nunca negativo, no máximo 2 casas decimais.
     * Não arredonda em silêncio: R$ 28,005 é recusado, porque arredondar
     * sem avisar mudaria um preço que a pessoa digitou.
     */
    public static BigDecimal valorMonetario(BigDecimal valor, String campo) {
        if (valor == null) {
            throw new RegraDeNegocioException(campo + " é obrigatório.");
        }
        if (valor.signum() < 0) {
            throw new RegraDeNegocioException(campo + " não pode ser negativo.");
        }
        if (valor.stripTrailingZeros().scale() > 2) {
            throw new RegraDeNegocioException(campo + " aceita no máximo 2 casas decimais.");
        }
        if (valor.compareTo(VALOR_MAXIMO) > 0) {
            throw new RegraDeNegocioException(campo + " é alto demais.");
        }
        return valor;
    }
}
