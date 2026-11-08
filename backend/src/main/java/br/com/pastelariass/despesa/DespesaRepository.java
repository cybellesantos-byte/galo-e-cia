package br.com.pastelariass.despesa;

import br.com.pastelariass.comum.Negocio;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DespesaRepository extends JpaRepository<Despesa, Long> {

    List<Despesa> findByNegocioAndDataDespesaBetweenOrderByDataDespesaDesc(Negocio negocio,
                                                                           LocalDate inicio, LocalDate fim);

    List<Despesa> findByDataDespesaBetweenOrderByDataDespesaDesc(LocalDate inicio, LocalDate fim);

    /** Despesas pagas em dinheiro de um caixa: cada uma tira valor da gaveta. */
    List<Despesa> findByCaixaId(Long caixaId);

    /**
     * Busca a despesa travada até o fim da transação. Duas pessoas pagando a
     * mesma despesa ao mesmo tempo não conseguem pagar as duas.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from Despesa d where d.id = :id")
    Optional<Despesa> buscarParaAlterar(@Param("id") Long id);
}
