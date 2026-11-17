package br.com.pastelariass.loja;

import br.com.pastelariass.comum.Negocio;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Consultas da loja. A chave é o negócio, então findById(Negocio.PASTELARIA)
 * já devolve a loja da pastelaria; não é preciso escrever mais nada.
 */
public interface LojaRepository extends JpaRepository<Loja, Negocio> {
}
