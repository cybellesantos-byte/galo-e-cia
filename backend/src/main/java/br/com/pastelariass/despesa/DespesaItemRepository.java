package br.com.pastelariass.despesa;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DespesaItemRepository extends JpaRepository<DespesaItem, Long> {

    List<DespesaItem> findByDespesaIdOrderByIdAsc(Long despesaId);
}
