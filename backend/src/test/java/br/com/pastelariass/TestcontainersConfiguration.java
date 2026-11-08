package br.com.pastelariass;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * PostgreSQL descartável para os testes, numa versão igual à do docker-compose.yml.
 *
 * @ServiceConnection faz o Spring apontar o datasource para este container
 * automaticamente. O banco real da pastelaria (porta 5435, volume próprio)
 * nunca é tocado pelos testes.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer(DockerImageName.parse("postgres:18.6"));
    }
}
