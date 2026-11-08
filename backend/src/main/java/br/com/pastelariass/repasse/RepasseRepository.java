package br.com.pastelariass.repasse;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface RepasseRepository extends JpaRepository<RepasseEntrega, Long> {

    Optional<RepasseEntrega> findByVendaId(Long vendaId);

    List<RepasseEntrega> findByDataEntregaBetweenOrderByDataEntregaDesc(LocalDate inicio, LocalDate fim);

    /** Repasses pagos em dinheiro de um caixa: cada um tira valor da gaveta. */
    List<RepasseEntrega> findByCaixaId(Long caixaId);

    /** Trava o repasse até o fim da transação: dois pagamentos ao mesmo tempo não passam juntos. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from RepasseEntrega r where r.id = :id")
    Optional<RepasseEntrega> buscarParaAlterar(@Param("id") Long id);

    /** O repasse de uma venda, travado: o cancelamento e o pagamento não se atropelam. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from RepasseEntrega r where r.vendaId = :vendaId")
    Optional<RepasseEntrega> buscarPorVendaParaAlterar(@Param("vendaId") Long vendaId);
}
