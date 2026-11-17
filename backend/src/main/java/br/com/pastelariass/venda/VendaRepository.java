package br.com.pastelariass.venda;

import br.com.pastelariass.comum.Negocio;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VendaRepository extends JpaRepository<Venda, Long> {

    Optional<Venda> findByChaveDaVenda(UUID chaveDaVenda);

    List<Venda> findByCaixaIdOrderByCriadoEmAsc(Long caixaId);

    /** Vendas em dinheiro que ainda contam para a gaveta (as não canceladas). */
    List<Venda> findByCaixaIdAndFormaPagamentoAndCanceladaFalse(Long caixaId, FormaPagamento formaPagamento);

    /**
     * Vendas de um negócio num intervalo de instantes, canceladas incluídas.
     * O negócio vem do caixa da venda.
     */
    @Query("select v from Venda v, Caixa c where v.caixaId = c.id and c.negocio = :negocio "
            + "and v.criadoEm >= :inicio and v.criadoEm < :fim order by v.criadoEm")
    List<Venda> listarDoNegocioNoPeriodo(@Param("negocio") Negocio negocio,
                                         @Param("inicio") Instant inicio,
                                         @Param("fim") Instant fim);

    /**
     * Só o número do caixa de uma venda, sem carregar a venda. Serve para travar
     * o caixa ANTES de ler a venda, então a leitura vê o que já foi gravado.
     */
    @Query("select v.caixaId from Venda v where v.id = :id")
    Optional<Long> buscarCaixaId(@Param("id") Long id);
}
