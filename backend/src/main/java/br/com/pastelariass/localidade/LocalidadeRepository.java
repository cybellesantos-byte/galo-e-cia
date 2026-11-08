package br.com.pastelariass.localidade;

import org.springframework.data.jpa.repository.JpaRepository;

/** Consultas de localidades. O Spring Data monta o SQL a partir do nome do método. */
public interface LocalidadeRepository extends JpaRepository<Localidade, Long> {

    boolean existsByNomeIgnoreCase(String nome);

    /** Na alteração, a própria localidade não conta como "nome repetido". */
    boolean existsByNomeIgnoreCaseAndIdNot(String nome, Long id);
}
