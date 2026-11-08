package br.com.pastelariass.venda;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface VendaItemRepository extends JpaRepository<VendaItem, Long> {

    List<VendaItem> findByVendaIdOrderByIdAsc(Long vendaId);
}
