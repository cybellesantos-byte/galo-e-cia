package br.com.pastelariass.auditoria;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** Só grava e lê. Não há método de alteração ou exclusão de registros. */
public interface RegistroAuditoriaRepository extends JpaRepository<RegistroAuditoria, Long> {

    /** Os 100 registros mais recentes, do mais novo para o mais antigo. */
    List<RegistroAuditoria> findTop100ByOrderByQuandoDesc();
}
