package br.com.pastelariass.loja;

import br.com.pastelariass.auditoria.AcaoAuditoria;
import br.com.pastelariass.auditoria.AuditoriaService;
import br.com.pastelariass.comum.Negocio;
import br.com.pastelariass.comum.RecursoNaoEncontradoException;
import br.com.pastelariass.comum.RegraDeNegocioException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Regras da loja: "estamos abertos?" e o número do WhatsApp que recebe os pedidos.
 * Quem consulta é o cardápio público; quem altera é a equipe, pelo painel.
 */
@Service
public class LojaService {

    private final LojaRepository lojaRepository;
    private final AuditoriaService auditoria;
    private final Clock relogio;

    public LojaService(LojaRepository lojaRepository, AuditoriaService auditoria, Clock relogio) {
        this.lojaRepository = lojaRepository;
        this.auditoria = auditoria;
        this.relogio = relogio;
    }

    @Transactional(readOnly = true)
    public Loja buscar(Negocio negocio) {
        return lojaRepository.findById(negocio)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Loja não encontrada."));
    }

    /** Abre ou fecha. O registro guarda o antes e o depois: "quem fechou a loja às 14h?" tem resposta. */
    @Transactional
    public Loja definirAberta(Negocio negocio, boolean aberta) {
        var loja = buscar(negocio);
        var antes = loja.isAberta();
        var usuario = auditoria.quemEstaLogado();
        loja.definirAberta(aberta, agora(), usuario);
        var salva = lojaRepository.saveAndFlush(loja);
        auditoria.registrar(usuario, AcaoAuditoria.LOJA_ALTERADA, negocio.name(), "aberta=" + antes + "->" + aberta);
        return salva;
    }

    /**
     * Guarda o número que recebe os pedidos, já limpo (só dígitos, com o 55 na frente).
     * Em branco apaga o número. Ver {@link #normalizarWhatsapp(String)}.
     */
    @Transactional
    public Loja definirWhatsapp(Negocio negocio, String texto) {
        var numero = normalizarWhatsapp(texto);
        var loja = buscar(negocio);
        var antes = loja.getWhatsapp();
        var usuario = auditoria.quemEstaLogado();
        loja.definirWhatsapp(numero, agora(), usuario);
        var salva = lojaRepository.saveAndFlush(loja);
        auditoria.registrar(usuario, AcaoAuditoria.LOJA_ALTERADA, negocio.name(), "whatsapp=" + antes + "->" + numero);
        return salva;
    }

    /**
     * "(83) 99999-8888" → "5583999998888". Aceita espaços, parênteses, hífen e "+";
     * qualquer outra coisa (letras, símbolos) é recusada. O número vira um link
     * (wa.me/...), e um link montado com texto livre poderia levar para outro lugar.
     *
     * Com 10 ou 11 dígitos (DDD + número), o 55 do Brasil é colocado na frente.
     * Em branco devolve null, que apaga o número.
     */
    static String normalizarWhatsapp(String texto) {
        var original = texto == null ? "" : texto.strip();
        if (original.isEmpty()) {
            return null;
        }
        if (!original.matches("[0-9 ()+\\-]+")) {
            throw new RegraDeNegocioException("O WhatsApp deve ter só números, com o DDD. Ex.: (83) 99999-8888.");
        }
        var digitos = original.replaceAll("[^0-9]", "");
        if (digitos.length() == 10 || digitos.length() == 11) {
            digitos = "55" + digitos;
        }
        if (!digitos.matches("55[0-9]{10,11}")) {
            throw new RegraDeNegocioException("O WhatsApp deve ter o DDD e o número. Ex.: (83) 99999-8888.");
        }
        return digitos;
    }

    private Instant agora() {
        // Microssegundos: a precisão do PostgreSQL. Sem isso, o valor lido do banco difere do gravado.
        return Instant.now(relogio).truncatedTo(ChronoUnit.MICROS);
    }
}
