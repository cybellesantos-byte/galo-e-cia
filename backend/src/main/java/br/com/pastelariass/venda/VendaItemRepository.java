package br.com.pastelariass.venda;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface VendaItemRepository extends JpaRepository<VendaItem, Long> {

    List<VendaItem> findByVendaIdOrderByIdAsc(Long vendaId);

    /**
     * Os itens de VÁRIAS vendas numa consulta só (em vez de uma por venda), na ordem em que foram
     * gravados. Quem chama deve repartir listas muito grandes em lotes (ver RelatorioService).
     */
    List<VendaItem> findByVendaIdInOrderByIdAsc(Collection<Long> vendaIds);
}
