package br.com.pastelariass.comum;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Relógio do sistema como um bean.
 *
 * Em vez de chamar Instant.now() direto, as classes recebem um Clock. No
 * sistema real ele é o relógio de verdade (UTC); nos testes podemos trocar
 * por um relógio parado numa data conhecida e conferir resultados exatos.
 * Isso vai ser essencial no caixa e nos relatórios ("vendas de hoje").
 */
@Configuration
public class RelogioConfig {

    @Bean
    public Clock relogio() {
        return Clock.systemUTC();
    }
}
