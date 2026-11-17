# Galo & Cia

Sistema de gestão da **Pastelaria do Galo** e da **peixaria** de uma família:
caixa, vendas, despesas, repasses ao entregador, relatórios e um **cardápio
público** para o cliente pedir pelo WhatsApp. Ele substitui o caderno e roda
de verdade, no dia a dia do negócio.

> 🎮 **Quer ver funcionando?** O painel real exige login e tem dados da família,
> então há uma **[demonstração clicável com dados fictícios](https://cybellesantos-byte.github.io/galo-e-cia-demo/)**:
> dá para vender, abrir e fechar o caixa e ver os relatórios.

O plano completo, com as etapas e as regras de negócio, está em
[docs/PLANO.md](docs/PLANO.md).

## Situação atual

**Fase 1 concluída (Etapas 0 a 10):** banco, login, usuários, produtos,
localidades, caixa, vendas (pastelaria e peixaria), despesas, repasses,
relatórios, auditoria, revisão de segurança e a **interface** (JavaScript puro +
Bootstrap, no computador e no celular).

**Fase 2 em andamento:** o sistema já roda na internet (Etapa 11: Dockerfile,
porta e endereço do cliente atrás do proxy) e tem o **cardápio público com
pedido pelo WhatsApp** (Etapa 12). Pedido e Pix automáticos (Etapas 13 em
diante) só entram se o volume de pedidos justificar; veja o plano.

## Tecnologias

| Parte | Tecnologia |
|---|---|
| Linguagem | Java 25 (LTS) |
| Framework | Spring Boot 4.1 (Web MVC, Data JPA, Security, Validation) |
| Interface | HTML, CSS, Bootstrap e JavaScript puro (módulos ES), sem build |
| Banco | PostgreSQL 18 no Docker, migrações com Flyway |
| Build | Maven, pelo Maven Wrapper (`mvnw`): não precisa instalar o Maven |
| Testes | JUnit 5, Mockito, Testcontainers (PostgreSQL real nos testes) |
| Entrega | Docker (Dockerfile do backend), hospedagem na nuvem |

## Pré-requisitos

- **JDK 25** e a variável `JAVA_HOME` apontando para ele
- **Docker Desktop** ligado ("Engine running")
- IntelliJ IDEA (recomendado)

## Primeira execução

**1. Crie o arquivo `.env`** na raiz do projeto, a partir do modelo:
```powershell
Copy-Item .env.example .env
```
Abra o `.env` e troque as senhas. O `.env` **nunca** vai para o Git.

| Variável | Para quê |
|---|---|
| `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD` | banco de dados (Docker e backend) |
| `DB_URL` | opcional; endereço completo do banco (`jdbc:postgresql://...`). Em casa **não definir**: vale `localhost:5435`. No servidor, aponta para o banco hospedado |
| `PORT` | opcional; porta em que o sistema liga. Em casa **não definir** (vale a 8080). O Render define sozinho |
| `ADMIN_USERNAME`, `ADMIN_PASSWORD` | administrador criado no **primeiro** start (senha com 12+ caracteres) |
| `SESSION_COOKIE_SECURE` | opcional; `true` só quando houver HTTPS |

**2. Suba o banco:**
```powershell
docker compose up -d
```
Confira com `docker compose ps`: o `pastelaria-ss-db` deve aparecer como **healthy**.

**3. Rode o sistema:** no IntelliJ, execute a classe `BackendApplication`.
Ou, pelo terminal:
```powershell
cd backend
.\mvnw.cmd spring-boot:run
```
Funcionou quando o log mostrar `Started BackendApplication`. A API fica em
`http://localhost:8080`. Na primeira vez, o Flyway aplica todas as migrações do
banco (V1 a V13) sozinho.

O Spring lê o `.env` sozinho (linha `spring.config.import` do
`application.properties`), seja iniciado da raiz ou da pasta `backend`.

## Testes

Precisam do **Docker ligado**: cada execução sobe um PostgreSQL descartável,
separado do banco da pastelaria (que nunca é tocado pelos testes).
```powershell
cd backend
.\mvnw.cmd test
```
A suíte tem 291 testes: unitários das regras de negócio e testes de API que
passam pela segurança e pelo banco real.

### Testes automáticos no GitHub (CI)

A cada PR e a cada envio para a `main`, o GitHub roda a suíte inteira sozinho
(arquivo `.github/workflows/testes.yml`): instala o Java 25, sobe o PostgreSQL
pelo Docker e executa os 291 testes. Leva cerca de 2 minutos.

- **No PR**, a caixa **Checks** mostra ✓ (passou) ou ✗ (falhou). Se for ✗, clique em
  **Details**: o log diz qual teste falhou e em que linha. O histórico de todas as
  execuções fica na aba **Actions** do repositório.
- **Só mescle com ✓.** O GitHub ainda **não impede** o merge com ✗ (a regra de proteção
  da `main` não está ligada), então a conferência é manual.
- O robô **não usa segredos nem o `.env`**: os testes sobem o próprio banco descartável.
- Para repetir no seu computador o que o robô faz: `.\mvnw.cmd test`, dentro de `backend`.
- O arquivo usa `sh mvnw` (e não `./mvnw`) porque o `mvnw` não tem permissão de execução
  no Git.
- Em repositório privado, o GitHub limita os minutos grátis por mês: se o robô passar a
  rodar muito, confira o limite atual em *Settings → Billing*.
- Provado em 15/11/2026: ✓ com o código certo e ✗ quando um teste foi quebrado de
  propósito (a branch de experimento foi fechada e apagada).
- **Ainda não ligado:** a regra na `main` exigindo ✓ e o Auto-Deploy do Render só depois
  dos testes. Hoje o Render publica sozinho depois do merge, sem esperar o resultado do robô.

## Regras financeiras

Estas regras valem em todo o sistema. Quando algo parecer estranho nos números,
comece por aqui.

1. **Pix e cartão não entram na gaveta.** O caixa separa o dinheiro físico dos recebimentos eletrônicos.
2. **Troco não é despesa.** Venda de R$ 32 paga com R$ 50 é uma venda de R$ 32, e entram R$ 32 na gaveta.
3. **Saldo inicial, reforço e sangria não são faturamento.** É dinheiro que só mudou de lugar.
4. **Faturamento não é lucro.** Cada número tem o nome certo e a fórmula está na seção de relatórios.
5. **Preço congelado na venda.** Mudar o preço ou a taxa depois não altera vendas antigas. Toda alteração de preço, custo ou taxa fica na auditoria.
6. **Nada se apaga, tudo se corrige.** Venda errada é cancelada com motivo e responsável. Caixa fechado não se altera.
7. **Custo desconhecido não é zero.** Margem sem custo cadastrado aparece como incompleta.
8. **Caixas separados não se misturam.** Venda de peixe só entra no caixa da peixaria, e vice-versa.
9. **Arredondamento por peso.** Total = preço por kg × peso, arredondado para centavos com "metade para cima". Exemplo: 1,333 kg × R$ 28,00 = R$ 37,324 → **R$ 37,32**.

### Fórmulas

**Esperado na gaveta** de um caixa:
```
saldo inicial
+ vendas em dinheiro (pelo total, sem o troco)
+ reforços
− sangrias
− despesas pagas em dinheiro
− repasses pagos em dinheiro
```

**Repasse ao entregador:** o maior entre a taxa padrão da localidade e a taxa cobrada.

**Resultado aproximado da peixaria:**
```
recebido (vendas pagas) − compras de peixe − outras despesas
```
É aproximado: o peixe comprado no fim do mês pode ser vendido no mês seguinte.
O comparativo mês a mês (`/api/relatorios/peixaria/compras-mensais`) mostra isso.

## Segurança e permissões

| Quem | Pode |
|---|---|
| Sem login | só entrar e pedir o token CSRF |
| **EQUIPE** | registrar vendas, despesas e consultas; alterar cadastros; abrir, sangrar e fechar caixa; pagar despesas |
| **ADMIN** | tudo o que a EQUIPE faz, mais: gerenciar usuários, **cancelar venda** e **pagar repasse** |

Cancelar venda e pagar repasse são só do ADMIN porque são devolução de dinheiro
e dinheiro que sai para o entregador. A regra fica no próprio controller
(`@PreAuthorize`), e não só no botão da tela.

### Checklist de segurança

| Item | Situação |
|---|---|
| Rotas fechadas por padrão | ✅ só login, CSRF, página de erro e o **cardápio público** (somente leitura, sem custo nem margem, com limite de acessos por endereço) são abertos |
| Senhas só como hash BCrypt | ✅ |
| Sessão com cookie HttpOnly; CSRF ativo | ✅ |
| Permissões checadas no backend | ✅ `@PreAuthorize` em usuários, cancelamento e repasse |
| Toda entrada validada | ✅ Bean Validation na API, regras repetidas no service |
| Erros sem detalhes internos | ✅ erro inesperado vira "Erro interno" e vai para o log |
| Segredos fora do código e dos logs | ✅ senhas só no `.env`; nada sensível é logado |
| Transações e concorrência | ✅ caixa, venda, repasse e despesa travam a linha ao alterar |
| Auditoria das operações financeiras | ✅ caixa, venda, despesa, repasse, preço, custo e taxa |
| Limite de tentativas de login | ✅ 5 falhas em 15 min bloqueiam o nome por 15 min |
| Política de conteúdo do navegador (CSP) | ✅ scripts, estilos, imagens e letras só do próprio sistema, sem `unsafe-inline` nem `unsafe-eval` (ver abaixo) |
| Alerta de sistema fora do ar | ✅ o Render avisa por e-mail quando uma publicação falha; o UptimeRobot visita o cardápio (que precisa do banco) a cada 5 minutos e avisa por e-mail. Testado com um alarme de propósito |
| Migrações nunca editadas depois de aplicadas | ✅ cada mudança virou uma migração nova (V1 a V14) |
| Nunca apagar volumes do Docker | ✅ ver a seção de segurança do banco |
| Backup com restauração testada | ✅ teste automatizado (ver abaixo); banco online com backup diário pelo GitHub, restauração provada |

### Política de conteúdo do navegador (CSP)

O sistema manda, em **toda resposta**, o cabeçalho `Content-Security-Policy` (definido em
`SecurityConfig.POLITICA_DE_CONTEUDO`). Ele diz ao navegador que scripts, estilos, imagens,
letras e conexões só podem vir **do próprio sistema** (as imagens também aceitam `data:`, porque
o Bootstrap traz ícones embutidos no CSS). Um script injetado numa página, mesmo que alguém
conseguisse colocá-lo lá, **não executa**.

**Para quem mexe nas telas** (HTML e JavaScript):

- **Não escreva** `style="..."`, `onclick="..."` nem `<script>` solto, nem `<style>`, no HTML nem em
  texto montado pelo JavaScript: o navegador bloqueia, e o elemento fica sem estilo, sem erro visível.
- Para um tamanho que muda (barras de gráfico), guarde o valor em `data-...` e aplique com
  `elemento.style.width = ...` depois de desenhar (exemplo: `aplicarTamanhos` em
  `js/telas/relatorios.js`).
- Se um dia precisar carregar algo de fora (um CDN, uma fonte do Google), é preciso acrescentar o
  endereço na política **de propósito**. Não use `'unsafe-inline'` para "fazer funcionar".
- Os testes `PoliticaDeConteudoTest` (olha os arquivos da tela) e `TelaPublicaApiTest` (confere o
  cabeçalho) acusam o erro antes de ele ir ao ar.

**Como foi validado (16/11/2026):** primeiro em modo de aviso (`reportOnly`), percorrendo todas as
telas num sistema de teste: zero avisos. Depois valendo de verdade: as 11 telas, os gráficos, os
menus, o cardápio do cliente e o botão "Pedir pelo WhatsApp" funcionaram, e um estilo e um script
injetados à força foram **bloqueados**.

## Backup e restauração

Os scripts ficam na pasta `scripts/`. Os backups vão para `backups/`, que o git
ignora, porque contêm dados reais.

**Gerar um backup** (só lê o banco; não altera nada):
```powershell
powershell -ExecutionPolicy Bypass -File scripts\backup.ps1
```

**Testar a restauração** (gera um backup, restaura num banco temporário,
compara as tabelas com o original e apaga o temporário):
```powershell
powershell -ExecutionPolicy Bypass -File scripts\testar-restauracao.ps1
```
Um backup só serve se restaura. Rode este teste antes de confiar no backup, e
sempre que mudar a estrutura do banco.

**Backup automático diário.** Pelo terminal, como administrador, agende às 23h30:
```powershell
schtasks /Create /SC DAILY /ST 23:30 /TN "Backup Pastelaria" /TR "powershell -NoProfile -ExecutionPolicy Bypass -File <caminho do projeto>\scripts\backup.ps1"
```
O backup só funciona com o Docker Desktop ligado no horário.

**Guarde cópias fora do computador.** Um backup na mesma máquina não protege
contra roubo, incêndio ou disco quebrado. Copie a pasta `backups/` para outro
lugar com regularidade.

### Backup do banco online (Aiven), pelo GitHub

Todo dia às **03h (Brasília)**, o robô `.github/workflows/backup.yml` tira uma cópia do
banco do Aiven, confere que ela abre e tem as 13 tabelas, **tranca com senha** (AES-256) e
guarda só o arquivo trancado por **30 dias**. Também roda na hora: aba **Actions → Backup do
banco → Run workflow**. Se o robô falhar, o GitHub manda e-mail.

- **Dois segredos** no GitHub (*Settings → Secrets and variables → Actions*):
  `AIVEN_DB_PASSWORD` (senha do banco) e `BACKUP_PASSPHRASE` (a senha que tranca as cópias).
  **Guarde a `BACKUP_PASSPHRASE` também no gerenciador de senhas: se for perdida, as cópias não
  abrem nunca mais.** Se a senha do Aiven mudar, atualize `AIVEN_DB_PASSWORD` aqui e
  `DB_PASSWORD` no Render.
- O GitHub guarda só 30 dias. Para guardar mais, baixe a cópia.
- O robô precisa que o Aiven aceite conexões de qualquer lugar (como está hoje, protegido por
  senha e conexão criptografada): por isso a lista de IPs do Aiven não é restringida.

**Abrir uma cópia e restaurar** (provado em 16/11/2026: restaurada num banco de teste, as 13
tabelas tinham os mesmos números do Aiven):

1. **Baixar:** aba *Actions*, a execução do "Backup do banco", seção *Artifacts*; descompactar
   o zip. Ou `gh run download <id da execução> -n <nome do artefato>`.
2. **Destrancar** (pede a `BACKUP_PASSPHRASE`; o `gpg` vem junto com o Git):
```powershell
& "C:\Program Files\Git\usr\bin\gpg.exe" -o galo.dump --decrypt galo-AAAAMMDD-HHMMSS.dump.gpg
```
3. **Restaurar num banco de teste**, nunca por cima do banco de verdade:
```powershell
docker run --rm -it -v "<pasta do galo.dump>:/b:ro" postgres:18.6 pg_restore -h <banco> -p <porta> -U <usuario> -d <nome do banco> -W --no-owner --no-acl /b/galo.dump
```
4. **Conferir:** rode `scripts/contar-linhas.sql` no banco restaurado e no de verdade e compare
   os números (a cópia pode ter um pouco menos, se houve vendas depois dela).
5. **Apagar o `galo.dump` destrancado** quando terminar: ele tem dados reais em texto aberto.

## API disponível

Toda requisição que **altera** dados (POST, PUT, DELETE) precisa do header
`X-XSRF-TOKEN` com o valor do cookie `XSRF-TOKEN` (proteção CSRF). Erros sempre
vêm no formato `{"mensagem": "...", "detalhes": [...]}`.

| Área | Endpoints | Quem |
|---|---|---|
| Login | `GET /api/auth/csrf`, `POST /api/auth/login`, `GET /api/auth/me`, `POST /api/auth/logout`, `PUT /api/auth/senha` | login: sem; o resto: logado |
| Usuários | `GET e POST /api/usuarios`, `POST /api/usuarios/{id}/desativar`, `POST /api/usuarios/{id}/reativar` | ADMIN |
| Auditoria | `GET /api/auditoria` (últimas 100 ações) | ADMIN |
| Produtos | `GET e POST /api/produtos`, `PUT /api/produtos/{id}`, `PUT /api/produtos/{id}/disponivel`, `PUT /api/produtos/{id}/no-cardapio`, `POST /api/produtos/{id}/desativar`, `POST /api/produtos/{id}/reativar` | logado |
| Localidades | `GET e POST /api/localidades`, `PUT /api/localidades/{id}` | logado |
| Caixas | `GET e POST /api/caixas`, `GET /api/caixas/aberto?negocio=`, `GET /api/caixas/{id}`, `POST /api/caixas/{id}/reforcos`, `POST /api/caixas/{id}/sangrias`, `POST /api/caixas/{id}/fechar` | logado |
| Vendas | `GET e POST /api/vendas`, `GET /api/vendas/{id}`, `POST /api/vendas/{id}/confirmar-pagamento`, `POST /api/vendas/{id}/cancelar` | cancelar: ADMIN; o resto: logado |
| Despesas | `GET e POST /api/despesas`, `POST /api/despesas/{id}/pagar`, `GET /api/despesas/resumo` | logado |
| Repasses | `GET /api/repasses`, `GET /api/repasses/resumo`, `POST /api/repasses/{id}/pagar` | pagar: ADMIN; o resto: logado |
| Relatórios | `GET /api/relatorios/pastelaria`, `/peixaria`, `/geral` (com `inicio` e `fim`), `GET /api/relatorios/peixaria/compras-mensais?ano=` | ADMIN |
| Loja (cardápio online) | `GET /api/loja/{negocio}`, `PUT /api/loja/{negocio}/aberta`, `PUT /api/loja/{negocio}/whatsapp` | abrir/fechar: logado; número do WhatsApp: ADMIN |
| **Cardápio público** | `GET /api/publico/cardapio` (e a página `/cardapio`) | **sem login**, só leitura |

## Problemas comuns

| Sintoma | Causa provável | Solução |
|---|---|---|
| `password authentication failed for user "pastelaria"` | o `.env` não foi encontrado, ou a senha nele não é a do banco | confira se o `.env` existe na raiz do projeto |
| `release version 25 not supported` | o Maven está usando um Java mais antigo | aponte o `JAVA_HOME` para o JDK 25 e reabra o terminal/IntelliJ |
| `JAVA_HOME` "certo", mas não funciona | caractere invisível (espaço, quebra de linha) no valor | rode `echo "[$env:JAVA_HOME]"`: o caminho deve aparecer colado nos colchetes |
| `Defina ADMIN_PASSWORD no .env` | primeiro start sem a senha do admin, ou com menos de 12 caracteres | preencha `ADMIN_PASSWORD` no `.env` |
| Testes falham ao iniciar o container | Docker Desktop desligado | ligue o Docker e espere "Engine running" |
| Aviso de *orphan containers* no `docker compose` | container antigo de outro projeto com o mesmo nome | **ignore**; não use `--remove-orphans` |
| `Valor inválido para negocio.` (400) | valor de parâmetro fora da lista (ex.: `?negocio=XPTO`) | use `PASTELARIA` ou `PEIXARIA` |
| `Abra o caixa da ... antes de ...` (422) | a operação precisa de caixa aberto do negócio | abra o caixa antes de vender, pagar em dinheiro ou sangrar |

## Regras de segurança do banco

- ⚠️ **Nunca** rode `docker compose down -v`: o `-v` **apaga** todos os dados.
- **Nunca** edite uma migração já aplicada (`V1__...sql`). Mudanças viram um novo arquivo (`V14__...`).
- O banco só aceita conexões **deste computador** (`127.0.0.1:5435`).
- Mudar `ADMIN_PASSWORD` no `.env` **não** troca a senha de um admin que já existe.

## Estrutura

```
SistemaPastelaria/
├── docker-compose.yml          banco PostgreSQL
├── .env.example                modelo das variáveis (o .env real não vai para o Git)
├── docs/PLANO.md               plano de implementação
├── backend/Dockerfile          imagem do sistema para a hospedagem
├── .github/workflows/          robô de testes no GitHub (CI)
├── scripts/
│   ├── backup.ps1              gera um backup do banco
│   ├── testar-restauracao.ps1  prova que o backup restaura
│   └── contar-linhas.sql       conta as linhas de cada tabela (para conferir uma restauração)
└── backend/
    └── src/main/java/br/com/pastelariass/
        ├── usuario/            usuários, papéis e administrador inicial
        ├── seguranca/          login, sessão, CSRF e limite de tentativas
        ├── auditoria/          diário de quem fez o quê
        ├── produto/            produtos (pastelaria e peixaria)
        ├── localidade/         localidades de entrega e taxas
        ├── caixa/              caixa por negócio, sangrias e reforços
        ├── venda/              vendas, pagamentos e cancelamentos
        ├── despesa/            despesas e compras de peixe
        ├── repasse/            repasses ao entregador
        ├── relatorio/          relatórios por negócio e gerais
        ├── loja/               cardápio aberto/fechado e WhatsApp do negócio
        ├── publico/            cardápio público (só leitura) e limite de acessos
        └── comum/              erros padronizados, validações e relógio
```
Os pacotes são organizados **por funcionalidade**: tudo de um assunto fica junto.

## Portas usadas

| Serviço | Porta |
|---|---|
| PostgreSQL da pastelaria | 5435 |
| Backend | 8080 |

As portas 5432 (PostgreSQL do Windows), 5433 (EduPlanner) e 5434 (Vitallis)
pertencem a outros projetos.
