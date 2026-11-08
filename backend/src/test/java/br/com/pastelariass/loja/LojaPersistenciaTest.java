package br.com.pastelariass.loja;

import br.com.pastelariass.TestcontainersConfiguration;
import br.com.pastelariass.auditoria.AcaoAuditoria;
import br.com.pastelariass.auditoria.RegistroAuditoriaRepository;
import br.com.pastelariass.comum.Negocio;
import br.com.pastelariass.comum.RegraDeNegocioException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A loja contra o PostgreSQL real: a migração V13 criou as duas linhas fechadas,
 * a equipe liga e desliga, o número é validado e tudo deixa rastro na auditoria.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@Transactional   // cada teste é desfeito no fim: não deixa a loja "aberta" para os outros
class LojaPersistenciaTest {

    @Autowired LojaService lojaService;
    @Autowired RegistroAuditoriaRepository registroAuditoria;
    @Autowired JdbcTemplate jdbc;

    @Test
    void asDuasLojasNascemFechadasESemWhatsapp() {
        for (var negocio : Negocio.values()) {
            var loja = lojaService.buscar(negocio);
            assertThat(loja.isAberta()).as("aberta: " + negocio).isFalse();
            assertThat(loja.getWhatsapp()).as("whatsapp: " + negocio).isNull();
        }
    }

    @Test
    void abrirEFecharGuardaQuemEQuandoEDeixaRastro() {
        var antes = registroAuditoria.count();

        var aberta = lojaService.definirAberta(Negocio.PASTELARIA, true);
        assertThat(aberta.isAberta()).isTrue();
        assertThat(aberta.getAlteradoPor()).isEqualTo("sistema");   // sem ninguém logado no teste
        assertThat(aberta.getAlteradoEm()).isNotNull();

        lojaService.definirAberta(Negocio.PASTELARIA, false);
        assertThat(lojaService.buscar(Negocio.PASTELARIA).isAberta()).isFalse();

        var detalhes = registroAuditoria.findAll().stream()
                .filter(r -> r.getAcao() == AcaoAuditoria.LOJA_ALTERADA)
                .map(r -> r.getDetalhes())
                .toList();
        assertThat(registroAuditoria.count()).isEqualTo(antes + 2);
        assertThat(detalhes).contains("aberta=false->true", "aberta=true->false");
    }

    @Test
    void abrirAPastelariaNaoAbreAPeixaria() {
        lojaService.definirAberta(Negocio.PASTELARIA, true);

        assertThat(lojaService.buscar(Negocio.PEIXARIA).isAberta()).isFalse();
    }

    @Test
    void whatsappEhGuardadoLimpoERecusaLixo() {
        var loja = lojaService.definirWhatsapp(Negocio.PASTELARIA, "(83) 99999-8888");
        assertThat(loja.getWhatsapp()).isEqualTo("5583999998888");

        assertThatThrownBy(() -> lojaService.definirWhatsapp(Negocio.PASTELARIA, "83 abc"))
                .isInstanceOf(RegraDeNegocioException.class);
        // O número anterior continua valendo.
        assertThat(lojaService.buscar(Negocio.PASTELARIA).getWhatsapp()).isEqualTo("5583999998888");

        lojaService.definirWhatsapp(Negocio.PASTELARIA, "");
        assertThat(lojaService.buscar(Negocio.PASTELARIA).getWhatsapp()).isNull();
    }

    /**
     * A última linha de defesa: mesmo que alguém grave direto, o banco recusa número estranho.
     * Sem transação do teste: depois de um erro o PostgreSQL deixa a transação "morta", e
     * cada tentativa precisa ser independente. Nada é gravado (as duas são recusadas).
     */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void oBancoTambemRecusaWhatsappForaDoFormato() {
        assertThatThrownBy(() -> jdbc.update("UPDATE loja SET whatsapp = 'abc' WHERE negocio = 'PASTELARIA'"))
                .hasMessageContaining("loja_whatsapp_check");
        assertThatThrownBy(() -> jdbc.update("UPDATE loja SET whatsapp = '55839999/../x' WHERE negocio = 'PASTELARIA'"))
                .hasMessageContaining("loja_whatsapp_check");
    }
}
