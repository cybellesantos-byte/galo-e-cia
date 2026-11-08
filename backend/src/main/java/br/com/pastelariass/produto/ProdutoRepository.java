package br.com.pastelariass.produto;

import br.com.pastelariass.comum.Negocio;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Consultas de produtos. Como em UsuarioRepository, o Spring Data monta o SQL
 * a partir do nome do método. "IgnoreCase" compara sem maiúsculas.
 */
public interface ProdutoRepository extends JpaRepository<Produto, Long> {

    List<Produto> findByNegocio(Negocio negocio, Sort ordem);

    boolean existsByNegocioAndNomeIgnoreCase(Negocio negocio, String nome);

    /** Na alteração, o próprio produto não conta como "nome repetido". */
    boolean existsByNegocioAndNomeIgnoreCaseAndIdNot(Negocio negocio, String nome, Long id);
}
