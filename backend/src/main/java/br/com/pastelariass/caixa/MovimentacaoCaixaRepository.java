package br.com.pastelariass.caixa;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MovimentacaoCaixaRepository extends JpaRepository<MovimentacaoCaixa, Long> {

    /** As movimentações de um caixa, da mais antiga para a mais recente. */
    List<MovimentacaoCaixa> findByCaixaIdOrderByQuandoAsc(Long caixaId);
}
