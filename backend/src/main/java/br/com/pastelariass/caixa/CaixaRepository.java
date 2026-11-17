package br.com.pastelariass.caixa;

import br.com.pastelariass.comum.Negocio;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

public interface CaixaRepository extends JpaRepository<Caixa, Long> {

    /** O caixa aberto deste negócio, se houver. O índice único garante que há no máximo um. */
    Optional<Caixa> findByNegocioAndFechadoEmIsNull(Negocio negocio);

    List<Caixa> findByNegocio(Negocio negocio, Sort ordem);

    /**
     * Busca o caixa TRAVANDO a linha até o fim da transação. Duas sangrias ao
     * mesmo tempo esperam uma pela outra, em vez de as duas lerem o mesmo saldo
     * e juntas retirarem mais dinheiro do que existe.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Caixa c where c.id = :id")
    Optional<Caixa> buscarParaAlterar(@Param("id") Long id);
}
