package br.com.pastelariass;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Ponto de partida do sistema.
 * O Spring procura componentes (@Controller, @Service...) neste pacote
 * e em todos os subpacotes; por isso esta classe fica na raiz.
 */
@SpringBootApplication
public class BackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(BackendApplication.class, args);
    }
}
