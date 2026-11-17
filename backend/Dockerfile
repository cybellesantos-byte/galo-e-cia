# Receita para montar e ligar o Galo & Cia (usada pelo Render).
# Contexto de build = esta pasta (backend). No Render: "Root Directory" = backend.

# ── Fase 1: a cozinha de montagem ───────────────────────────────────────────
# JDK completo + Maven. Só serve para gerar o .jar; não vai para a imagem final.
FROM eclipse-temurin:25-jdk AS construcao
WORKDIR /app

# Primeiro só o que define as bibliotecas. Elas mudam pouco, então o Docker
# guarda este passo em cache e os próximos builds não baixam tudo de novo.
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN sh mvnw -q dependency:go-offline

# Agora o código (que muda sempre) e a montagem do .jar.
# Os testes NÃO rodam aqui: eles precisam do Docker (Testcontainers) e rodam
# no computador, antes do commit.
COPY src src
RUN sh mvnw -q package -DskipTests

# ── Fase 2: a loja que vai ao ar ────────────────────────────────────────────
# Só o Java mínimo para rodar + o .jar pronto. Menor e mais segura.
FROM eclipse-temurin:25-jre

# Usuário comum, sem poderes de administrador dentro da caixa.
RUN useradd --system --no-create-home galo
WORKDIR /app
COPY --from=construcao /app/target/*.jar app.jar
USER galo

# O Java usa até 75% da memória da caixa (o plano do Render tem limite).
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"

# Porta padrão. No Render, a variável PORT manda (ver application.properties).
EXPOSE 8080

# Nenhuma senha aqui: banco, admin e chaves entram por variáveis de ambiente.
ENTRYPOINT ["java", "-jar", "app.jar"]
