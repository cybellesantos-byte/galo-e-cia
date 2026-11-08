# Plano de implementação — Galo & Cia (Pastelaria do Galo e Peixaria)

> Sistema **interno** (usado só pela família e pela equipe) para controlar dois
> negócios da mesma casa: a **pastelaria** e a **peixaria**.
> Foco atual: **backend**. A interface vem no final, em **JavaScript puro + Bootstrap**.

---

## 1. Visão geral

**Um sistema, dois negócios, caixas separados.**

| Módulo | Pastelaria | Peixaria | Responde à pergunta... |
|---|:-:|:-:|---|
| Usuários e permissões | ✔ | ✔ | Quem está mexendo no sistema? |
| Auditoria | ✔ | ✔ | Quem fez o quê, e quando? |
| Produtos | ✔ (por unidade) | ✔ (por kg) | O que vendemos e por quanto? |
| Localidades de entrega | ✔ | — | Para onde entregamos e qual a taxa? |
| Caixa | ✔ | ✔ | Quanto dinheiro deveria ter na gaveta? |
| Vendas | ✔ | ✔ | O que foi vendido e como foi pago? |
| Despesas e compras | ✔ | ✔ (compras de peixe) | Com o que gastamos? |
| Repasses ao entregador | ✔ | — | Quanto devemos ao entregador? |
| Relatórios | ✔ | ✔ | Como foi o dia, a semana, o mês? |

---

## 2. Decisões de arquitetura

| Decisão | Por quê |
|---|---|
| **Um único projeto Spring Boot** (monólito) | Microserviços servem para empresas grandes. Aqui só trariam complexidade. |
| **Um sistema para os dois negócios** | 80% das regras são iguais (caixa, vendas, despesas, relatórios). Dois sistemas = escrever e corrigir tudo duas vezes. |
| **`Negocio` como enum (`PASTELARIA`, `PEIXARIA`)** | São dois negócios fixos. Uma tabela só valeria a pena se fossem surgir negócios novos com frequência. |
| **Pacotes por funcionalidade** (`caixa/`, `venda/`...) | Tudo de um módulo fica junto. Dentro de cada pasta: Controller → Service → Repository → Entity. |
| **PostgreSQL + Flyway** | O banco é criado por arquivos SQL numerados e versionados. O Hibernate só confere (`ddl-auto=validate`). |
| **Login com sessão + cookie HttpOnly + CSRF** | Mais simples e seguro que JWT para um sistema interno. |
| **Frontend servido pelo próprio Spring Boot** (`resources/static`) | Mesmo endereço → sem CORS, e tudo roda com um botão no IntelliJ. |
| **`BigDecimal` / `NUMERIC`** para dinheiro e peso | `double` erra centavos (`0.1 + 0.2 = 0.30000000000000004`). |
| **Datas em UTC, telas e filtros em `America/Sao_Paulo`** | "Vendas de hoje" = hoje no horário do Brasil. |

---

## 3. Regras de ouro do dinheiro

1. **Pix não entra na gaveta.** O caixa separa *dinheiro físico* de *recebimentos Pix*.
2. **Troco não é despesa.** Venda de R$ 32 paga com R$ 50 → a venda é R$ 32 e entram R$ 32 líquidos na gaveta.
3. **Saldo inicial e reforço não são faturamento.** É dinheiro que só mudou de lugar.
4. **Faturamento ≠ lucro.** Cada número aparece com o nome certo e a fórmula.
5. **Preço congelado na venda.** Mudar o preço amanhã não altera vendas antigas.
6. **Nada se apaga, tudo se corrige.** Venda errada é *cancelada* com motivo. Caixa fechado não se edita: corrige-se com lançamento de ajuste.
7. **Custo desconhecido não é zero.** Margem sem custo cadastrado aparece como "incompleta".
8. **Caixas separados não se misturam.** Venda de peixe só entra no caixa da peixaria, e vice-versa.
9. **Arredondamento por peso é regra de negócio.** Total = preço/kg × peso, arredondado para centavos com "metade para cima" (`HALF_UP`). Ex.: 1,333 kg × R$ 28,00 = R$ 37,324 → **R$ 37,32**.

---

## 4. As etapas

Ritmo de cada etapa: **explico → implemento um pedaço → você roda → dúvidas → próximo pedaço**.
Cada etapa termina funcionando e testada.

**Situação (29/10/2026):** ✅ **Fase 1 concluída: Etapas 0 a 10 prontas**, testadas e na `main`.
Próximo passo: usar o sistema na pastelaria e na peixaria antes de começar a Fase 2.

### Etapa 0 — Fundação
- Projeto Spring Boot (pom.xml, Maven Wrapper)
- PostgreSQL no Docker (porta **5435**, volume próprio, sem tocar nos outros projetos)
- Variáveis de ambiente no IntelliJ
- Flyway: primeira tabela `usuario`
- Spring Security: login/logout pela API, BCrypt, CSRF
- Administrador inicial criado automaticamente
- Erros padronizados em JSON
- Requisições de teste com arquivo `.http` do IntelliJ (ou Postman/curl)

**Pronto quando:** você faz login pelo `.http`, e o acesso sem login é bloqueado.

### Etapa 1 — Usuários, permissões e auditoria
- Papéis: **ADMIN** (tudo) e **EQUIPE** (operação do dia a dia)
- ADMIN cadastra, desativa e troca a senha de usuários (nunca apaga)
- Tabela de auditoria: quem fez o quê e quando

**Pronto quando:** a EQUIPE tenta uma ação de ADMIN e recebe "acesso negado".

### Etapa 2 — Produtos e localidades
- Produto: **negócio**, nome, categoria, **unidade de venda** (`UNIDADE` ou `KG`), preço (por unidade ou por kg), custo estimado (opcional)
- **Ativo** (faz parte do cadastro) × **Disponível** (dá para vender agora)
- Localidades da pastelaria: Lagoa de Praia R$ 3, Barra de Mamanguape R$ 5, Praia de Campina R$ 5, Saco R$ 6, Tanques R$ 7

**Pronto quando:** você esgota "Pastel de carne" sem apagá-lo, e cadastra "Tilápia — R$ 28,00/kg".

### Etapa 3 — Caixa (um por negócio)
- Abrir caixa com saldo inicial; **no máximo um caixa aberto por negócio** (garantido pelo banco)
- **Sangria** (retirar dinheiro) e **reforço** (colocar troco)
- Fechar: valor contado × esperado → diferença + observação
- Caixa fechado não pode ser alterado

**Fórmula do dinheiro esperado na gaveta:**
```
saldo inicial
+ vendas recebidas em dinheiro (líquido, já sem o troco)
+ reforços
− sangrias
− despesas pagas com dinheiro deste caixa
− repasses pagos com dinheiro deste caixa
```

**Pronto quando:** abre com R$ 100, sangria de R$ 30, conta R$ 68 → "esperado R$ 70, diferença −R$ 2".

### Etapa 4 — Vendas da pastelaria
- Itens por unidade ("2× Pastel de carne + 1× Coca"); **o backend calcula o total**
- Origem: balcão ou WhatsApp. Tipo: retirada ou entrega (entrega → localidade → taxa)
- Pagamento: Pix (confirmado pela equipe ao ver no app do banco) ou dinheiro ("troco para quanto?")
- Situação do pagamento: pendente / pago
- Cancelamento com motivo e responsável
- Produto esgotado não pode ser vendido; proteção contra venda duplicada

**Pronto quando:** venda com troco aparece no caixa aberto e o esperado bate.

### Etapa 5 — Vendas da peixaria (por peso)
- Reaproveita tudo da Etapa 4; a diferença é a quantidade em **kg com 3 casas** (`1,350`)
- Total do item = preço/kg × peso, arredondado (regra 9)
- Peixe grande que passa de 1 kg na sacola: é só o peso real × preço/kg
- Sem entrega e sem taxa nesta versão
- Entra no **caixa da peixaria**

**Pronto quando:** 1,350 kg de tilápia a R$ 28,00/kg = R$ 37,80 no caixa da peixaria.

### Etapa 6 — Despesas e compras de peixe
- Despesa: negócio, descrição, categoria, valor, data, forma de pagamento, situação (paga/pendente), responsável, observação
- **Compra de peixe** é uma categoria de despesa da peixaria, com **peso em kg opcional** (permite ver o preço médio pago por kg)
- Paga com dinheiro do caixa → sai da gaveta; paga de outro jeito → não mexe na gaveta

**Pronto quando:** compra de R$ 500 (30 kg) aparece no gasto mensal da peixaria.

### Etapa 7 — Repasses ao entregador (pastelaria)
- Cada venda com entrega gera uma obrigação com a taxa congelada
- Consulta por semana ou período: pendentes × pagos
- Registrar o pagamento (data, valor, responsável, origem: caixa ou outro meio)
- **Impossível pagar a mesma entrega duas vezes**, mesmo com dois cliques simultâneos
- Repasse não vira despesa duplicada

### Etapa 8 — Relatórios
Filtros: dia, semana, mês, período personalizado — **por negócio** e **somando os dois**.

**Comuns:** quantidade de vendas, faturamento, Pix × dinheiro, valores pendentes, cancelamentos, despesas pagas, produtos mais vendidos, fechamentos e diferenças de caixa.

**Pastelaria:** taxas de entrega, vendas por origem, margem estimada por produto, repasses pendentes/pagos.

**Peixaria:** vendas por dia, kg vendidos por peixe, **gasto com compras de peixe no mês** (comparativo mês a mês), preço médio de compra por kg, **resultado da semana e do mês**.

**Fórmula do resultado (lucro aproximado) da peixaria:**
```
resultado do período = vendas recebidas − compras de peixe − outras despesas da peixaria
```
⚠️ É um resultado **aproximado**: o peixe comprado no fim de um mês pode ser vendido no mês seguinte. Por isso o relatório mostra também o mês a mês, onde essas diferenças se equilibram.

### Etapa 9 — Revisão final: segurança, backup e documentação
- Revisão completa do checklist de segurança
- Backup automático do banco **+ teste de restauração**
- README: como rodar, configurar, testar, regras financeiras

### Etapa 10 — Interface (JavaScript puro + Bootstrap) ✅
Telas: login, escolher negócio, caixa do dia, nova venda (rápida), produtos, despesas/compras, repasses, relatórios. Responsiva para celular.

Feito, além das telas pedidas: identidade visual da Pastelaria do Galo (`docs/IDENTIDADE_VISUAL.md`),
telas Equipe e Minha senha, pagamento de várias entregas de uma vez, conta aberta do
"esperado na gaveta" e custo guardado em cada item de venda (V12), para a margem de meses
passados não mudar quando o custo do cadastro muda. Relatórios só para o administrador.

---

## 5. Estratégia de testes

| Tipo | O que testa | Ferramenta | Exemplo |
|---|---|---|---|
| **Unitário** | Uma regra isolada, sem banco | JUnit + Mockito | troco de R$ 50 em venda de R$ 32 = R$ 18 |
| **Integração** | Código + banco real | Testcontainers (Postgres descartável) | não dá para abrir dois caixas da pastelaria |
| **API** | Requisição HTTP completa, com segurança | MockMvc | EQUIPE não consegue pagar repasse |

**Testes mais importantes:**
- Cálculos: subtotal, taxa, total, troco, **peso × preço/kg com arredondamento**
- Fechamento de caixa (esperado × contado)
- Pix não alterando o dinheiro físico
- **Venda da peixaria não entrando no caixa da pastelaria** (e vice-versa)
- Produto esgotado e venda duplicada bloqueados
- Repasse pago duas vezes (inclusive ao mesmo tempo)
- Caixa fechado não aceitando alterações
- Permissões: sem login → bloqueado; EQUIPE → só o que pode
- Resultado mensal da peixaria batendo com uma conta feita à mão

---

## 6. Checklist de segurança

- [x] Toda rota exige login (não há área pública nesta versão)
- [x] Senhas só como hash BCrypt
- [x] Sessão com cookie HttpOnly; CSRF ativo
- [x] Permissões checadas no backend (o frontend só esconde botões)
- [x] Toda entrada validada (Bean Validation)
- [x] Erros sem detalhes internos
- [x] Segredos fora do código e fora dos logs
- [x] Transações e controle de concorrência em caixa, vendas e repasses
- [x] Auditoria das operações financeiras
- [x] Limite de tentativas de login
- [x] Migrações nunca editadas depois de aplicadas
- [x] Nunca apagar volumes do Docker para "consertar" algo
- [x] Backup com restauração testada antes do uso real (scripts/testar-restauracao.ps1)

---

## 7. Fora desta versão (ideias futuras)

- Estoque de peixe em kg (compras − vendas − perdas) e registro de perdas
- Entrega de peixe
- ~~Cardápio online e pedidos pelo site~~ → planejado na **Fase 2** (seção 9)
- ~~Pix com confirmação automática~~ → planejado na **Fase 2** (seção 9)
- Controle de estoque de ingredientes da pastelaria

---

## 8. Pendências

- [ ] Confirmar o item "Outra coisa" marcado no escopo inicial (não foi especificado)

---

## 9. Fase 2 — Pedidos online (planejada, ainda não começou)

**Objetivo:** o cliente abre um link (do Instagram ou do WhatsApp), vê o cardápio
com o que tem **agora**, faz o pedido e paga em **Pix ou dinheiro**. Quando um
recheio acaba, a equipe marca "esgotado" e o cardápio para de oferecer na hora.

**Pré-requisito:** a Fase 1 (Etapas 0 a 10) terminada e **em uso na pastelaria**
por algumas semanas. Abrir para o público um sistema que ainda muda todo dia
multiplica os riscos.

### 9.1 Por que a segurança muda tanto

Hoje o sistema roda num computador da casa e só a família o alcança. Na
internet, **qualquer pessoa do mundo, e principalmente robôs**, vai tentar:
adivinhar senhas, mandar pedidos falsos, alterar preços pelo navegador, ler
pedidos de outras pessoas, derrubar o sistema com milhares de acessos.

Princípios que guiam toda a Fase 2:

| Princípio | Em palavras simples |
|---|---|
| **Defesa em camadas** | nenhuma proteção sozinha basta; se uma falhar, a próxima segura (como portão + porta + cofre) |
| **Nunca confiar no navegador** | tudo que vem do cliente pode ser falsificado: preço, total, "já paguei". O servidor recalcula e confere tudo |
| **Mínimo necessário** | pedir só os dados indispensáveis do cliente e mostrar só o necessário em cada tela |
| **Negar por padrão** | toda rota nova nasce fechada; abrir é decisão consciente |
| **Tudo registrado** | cada ação sensível vai para a auditoria |
| **Errar de forma segura** | na dúvida (provedor fora do ar, dado estranho), não cobrar, não liberar, avisar |

### 9.2 Decisões a tomar antes de começar

| Decisão | Opções | Recomendação inicial |
|---|---|---|
| **Onde o sistema vai rodar** | servidor alugado (VPS), plataforma gerenciada ou computador em casa | ✅ **Decidido: VPS alugada.** O sistema inteiro (painel interno + cardápio) vai para ela. Motivos: continua no ar se faltar luz ou internet em casa; não exige abrir portas na rede da família; e a dona do projeto quer aprender a cuidar de um servidor (Linux, SSH, Docker, firewall, HTTPS). **Falta escolher o fornecedor da VPS**, comparando preço, localização no Brasil, backup e facilidade, sem chutar. |
| **Painel interno na internet?** | (a) painel acessível de qualquer lugar, com login + verificação em duas etapas; (b) painel só na rede da pastelaria | (a), porque a família usa o celular; com **verificação em duas etapas obrigatória para ADMIN** |
| **Provedor de Pix** | ex.: Mercado Pago, Asaas, Efí, PagBank (lista a confirmar) | escolher **depois** de ler a documentação oficial atual e comparar: cobrança com QR code, aviso automático (webhook) com assinatura, devolução parcial, ambiente de testes, taxas, cadastro exigido (CPF, MEI ou CNPJ) |
| **Esgotado: ocultar ou mostrar?** | mostrar riscado, ou esconder do cardápio | mostrar como "esgotado hoje" (o cliente vê que existe e volta outro dia); configurável |

### 9.3 As etapas

Ritmo igual ao da Fase 1: explicar → implementar em partes → testes →
sabotagem → validação → commit com aprovação. Cada etapa termina utilizável.

#### Etapa 11 — Preparar o sistema para a internet
Antes de qualquer página pública, o **chão** tem de estar firme.
- Servidor com **HTTPS** (o cadeado: ninguém no meio do caminho lê ou altera os dados)
- **Proxy reverso** na frente do sistema; só as portas 80/443 abertas; **firewall**
- **Banco de dados sem acesso pela internet** (só o sistema conversa com ele)
- Senhas e chaves só em variáveis do servidor, nunca no código; `SESSION_COOKIE_SECURE=true`
- **Cabeçalhos de segurança** (CSP, HSTS, proteção contra a página ser embutida em outro site)
- **Verificação em duas etapas (TOTP)** para ADMIN: além da senha, um código do celular,
  **com códigos de recuperação** guardados em papel (perder o celular não pode trancar o
  admin fora do próprio sistema)
- **Endereço real do cliente atrás do proxy**: configurar o sistema para confiar só no
  proxy ao ler o endereço de origem. Errado, ou um robô burla o limite de requisições, ou
  todos parecem vir do mesmo endereço e clientes de verdade são bloqueados
- **Ambiente de testes separado** na VPS (outro endereço, outro banco), para as Etapas
  12 a 16 serem testadas sem dados reais de clientes
- **Mudança dos dados** do computador de casa para a VPS: backup do banco local,
  restauração na VPS, conferência (caixas, vendas, usuários) e só então desligar o local
- **Backup automático fora do servidor** + restauração testada no servidor novo
- Atualizações de segurança do sistema operacional e das bibliotecas, com rotina definida
- Alertas: sistema fora do ar, muitos logins falhando, erros em sequência
- Logs **sem** dados pessoais completos e **sem** senhas ou chaves

**Pronto quando:** o sistema interno funciona em `https://...`, só as portas
80/443 respondem, o banco não responde de fora, a restauração foi testada e os
dados da pastelaria foram migrados e conferidos.

#### Etapa 12 — Cardápio público + pedido pelo WhatsApp (o primeiro passo público)
É a versão **mínima e de baixo risco** da Fase 2 (o "MVP"): o cardápio só **mostra**
informações, e o pedido vai para o WhatsApp da pastelaria, já escrito.

Para o cliente:
1. abre o link do cardápio (Instagram, WhatsApp);
2. vê o que tem **hoje** (o esgotado aparece na hora);
3. escolhe itens e quantidades, retirada ou entrega (com o local), Pix ou dinheiro (e troco);
4. toca em **"Pedir pelo WhatsApp"**: abre a conversa com a pastelaria com o pedido
   montado (itens, taxa, **total estimado**, forma de pagamento e nome);
5. só aperta enviar.

Para a equipe: confere, confirma o valor com o cliente pelo próprio WhatsApp e lança a
venda na tela **Vender** (origem "WhatsApp"), como já faz hoje.

O que esta etapa **não** faz, de propósito: não cobra (Pix continua para a chave da
pastelaria, conferido pela equipe), não registra pedido sozinha, não guarda dados de
clientes. Por isso o risco é muito menor que o das Etapas 13 a 16.

- Rota pública **separada** (`/api/publico/...`), sem login, **só de leitura**
- Mostra só: nome, descrição, ingredientes informados, foto, preço, categoria, disponível.
  **Nunca** custo, margem, ids internos ou dados de vendas
- Esgotado aparece na hora: a página consulta de novo a cada poucos segundos
- Aberto/fechado, horários e "pausa por excesso de pedidos"
- **Limite de requisições por endereço de origem** (contra robôs e sobrecarga)
- Informações alimentares só quando cadastradas; nunca inventar "sem glúten" ou similares

- O pedido para o WhatsApp é montado pelo link oficial `wa.me/<número>?text=...`; o total
  aparece como **estimado** (quem confirma o valor final é a equipe)

**Pronto quando:** marcar "esgotado" no painel faz o cardápio público mostrar
"esgotado" em poucos segundos, sem recarregar a página, e o botão abre o WhatsApp da
pastelaria com o pedido escrito certo.

**Decisões da dona (30/10/2026) e andamento da Etapa 12** (branch `etapa-12/cardapio-publico`):
- O cardápio mostra **nome, preço e descrição** de cada produto (uma frase curta, opcional).
  Fotos ficam para depois.
- **A equipe liga e desliga** o "estamos abertos" (sem horário automático).
- O número do WhatsApp fica configurável por negócio (só dígitos, com o 55), e o banco recusa
  qualquer outro formato.
- Feito até aqui, **Parte A (dados)**: `produto.descricao` e a tabela `loja` (aberta, whatsapp,
  quem e quando alterou), migração V13. A loja nasce **fechada**. Abrir, fechar e trocar o número
  deixam rastro na auditoria.
- Feito, **Parte B (porta pública)**: `GET /api/publico/cardapio`, sem login, só leitura. Mostra
  nome, descrição, categoria, preço e se está esgotado, mais os locais de entrega com a taxa e
  se a loja está aberta e o WhatsApp. **Nunca** custo, margem, ids ou dados internos (a lista do
  que sai fica no arquivo `CardapioPublico.java`). Só a pastelaria; produto fora da lista não
  aparece. A liberação no `SecurityConfig` vale para esse endereço exato, e o resto do sistema
  continua trancado. Limite de **120 acessos por minuto por endereço** (configurável em
  `app.publico.limite-por-minuto`), com `429` e `Retry-After`.
  - Para a Etapa 11: atrás de um proxy, o "endereço" do limite precisa ser o do cliente de
    verdade, e não o do proxy. Configurar o sistema para confiar só no proxy.
- Feito, **Parte C (a página do cliente)**: `/cardapio`. Sem login e sem o menu do sistema interno.
  O cliente escolhe os itens, retirada ou entrega (com o local e o endereço), Pix ou dinheiro (com
  troco) e toca em "Pedir pelo WhatsApp", que abre a conversa com o pedido já escrito (itens, taxa,
  **total estimado**, pagamento, nome e observação).
  - O pedido **não é gravado no servidor**: existe só no aparelho do cliente e na conversa do WhatsApp.
  - A página pergunta o cardápio de novo a cada 10 segundos; item que esgota sai do pedido sozinho.
  - Na hora de enviar, o cardápio é **conferido de novo**: se algo esgotou, a loja fechou ou o total
    mudou, o pedido não sai e o cliente é avisado.
  - Segurança: todo texto vindo do servidor é escapado; o texto do cliente é limpo e limitado; o
    número do WhatsApp só vira link se estiver no formato 55 + DDD + número.
  - Pagamento: só Pix e dinheiro, como no plano. Cartão pode entrar depois, se a dona quiser.
- Feito, **Parte D (o painel da equipe)**: tela **Cardápio online** (`#/cardapio-online`, no menu e
  no "Mais" do celular).
  - Equipe e administrador **abrem e fecham** o cardápio, com um botão grande e a palavra
    ABERTO ou FECHADO. Quem mexeu e quando aparece na tela e fica na auditoria.
  - O cliente percebe a mudança em até 10 segundos, sem recarregar a página.
  - **Só o administrador troca o número do WhatsApp** (é para onde vão todos os pedidos), com
    uma janela de confirmação. A equipe vê o número, mas não o campo.
  - Se o cardápio está aberto sem número, a tela avisa que o cliente não vai conseguir pedir.
  - A tela mostra o link do cardápio, com "Copiar" e "Abrir como o cliente vê".
  - API: `GET /api/loja/{negocio}`, `PUT /api/loja/{negocio}/aberta` (equipe e administrador) e
    `PUT /api/loja/{negocio}/whatsapp` (só administrador).
- **A Etapa 12 está completa (A, B, C e D).** Falta usar de verdade e avaliar com a família,
  como o plano manda, e o servidor (Etapa 11) para o link abrir fora da rede da casa.
- O servidor (Etapa 11) fica para depois: até lá o cardápio só abre na rede local.

**Depois da Etapa 12, parar e avaliar** com a família, usando de verdade por algumas
semanas: o cardápio + WhatsApp já resolve? As Etapas 13 a 16 (pedido e Pix automáticos)
só começam se o volume de pedidos mostrar que valem o trabalho e o risco a mais.

#### Etapa 13 — Pedido online com pagamento em dinheiro
- Carrinho no navegador; **o servidor recalcula preço, taxa e total** e ignora valores enviados
- **Revalida disponibilidade ao finalizar**: se um item esgotou, recusa, diz qual e
  preserva o resto do carrinho. **Nunca troca sabor sem o cliente concordar**
- **Chave de idempotência**: clique duplo ou reenvio não gera pedido duplicado
- Dados mínimos: nome, telefone; endereço e referência **só** se for entrega
- Troco: "troco para quanto?", com validação
- Bloqueado no servidor quando a pastelaria está fechada ou pausada
- Limite de pedidos por endereço de origem e por telefone; armadilha simples contra robôs
- Opcional: **quantidade disponível** para itens com limite conhecido, com reserva
  transacional (duas pessoas não compram a última unidade ao mesmo tempo)

**Pronto quando:** um pedido feito no celular chega na fila da equipe com o total
calculado pelo servidor, e um item esgotado no meio do caminho é tratado sem perder o carrinho.

#### Etapa 14 — Aceitação, fila e acompanhamento
- Estados do pedido: aguardando pagamento → aguardando aceitação → aceito →
  em preparo → pronto → saiu para entrega → entregue (ou cancelado)
- **Situação do pedido, do pagamento e da devolução são campos separados**
- Equipe aceita ou recusa; alerta visual e sonoro opcional de pedido novo
- **Link de acompanhamento com código secreto aleatório** (128 bits). Nunca um número
  sequencial: com `/pedido/41`, alguém tentaria `/pedido/42` e veria o pedido de outra pessoa
- Página de acompanhamento sem dados pessoais desnecessários (telefone mascarado)
- Estimativas sempre como **estimativa**, nunca horário prometido

#### Etapa 15 — Pix automático em ambiente de testes
- **Pesquisa documentada** dos provedores (seção 9.2) antes de escrever código
- Uma cobrança por pedido, com o total incluindo a entrega; QR code, copia e cola e vencimento
- **Confirmação só pelo servidor**: o aviso do provedor (webhook) tem a **autenticidade
  verificada** e, além disso, o sistema **consulta o provedor** para confirmar valor e identificação
- **Nunca** aceitar como prova: botão "já paguei", comprovante, retorno do navegador
- Avisos repetidos do provedor não duplicam nada (idempotência)
- Cobrança expirada: avisar o cliente e permitir nova tentativa, revalidando a disponibilidade
- Pagamento que chega depois de expirar ou cancelar: tratado e devolvido, nunca esquecido
- Chaves do provedor **só no servidor**; nunca no navegador nem no Git
- **Conferência diária** (reconciliação): o que o provedor diz que recebeu × o que o sistema registrou
- Só começa a usar dinheiro de verdade numa etapa própria (Etapa 17)

#### Etapa 16 — Falta de produto depois do pedido e devoluções
- Remover só a unidade que faltou e devolver o valor dela (devolução parcial)
- Pix: devolução pelo provedor; dinheiro: devolução registrada no caixa
- Nunca devolver mais do que foi pago menos devoluções anteriores
- Estados da devolução: solicitada → em processamento → concluída / falhou
- Só ADMIN devolve; confirmação antes; idempotente; tudo na auditoria
- Se o provedor responder e o banco falhar (ou o contrário), há rotina de
  **recuperação**: a chamada externa e o banco não são uma transação única

#### Etapa 17 — Entrada em produção
- Checklist de segurança da Fase 2 inteiro conferido
- Varredura automática de vulnerabilidades no endereço de testes (ex.: OWASP ZAP)
- **Política de privacidade** e prazo para apagar ou anonimizar dados de clientes (LGPD)
- Pedidos-piloto com a família antes de divulgar o link
- Plano de volta atrás: como desligar os pedidos online em 1 minuto se algo der errado
- Troca do Pix de testes para o de verdade **só com aprovação explícita**

### 9.4 Checklist de segurança da Fase 2

- [ ] HTTPS em tudo; cookies `Secure`; HSTS
- [ ] Banco e portas internas fechados para a internet; firewall
- [ ] Verificação em duas etapas para ADMIN
- [ ] Rotas públicas separadas, só com o necessário, validadas e com limite de requisições
- [ ] Preços, taxas e totais sempre calculados no servidor
- [ ] Idempotência em pedidos, pagamentos, devoluções
- [ ] Links de acompanhamento com código aleatório, nunca número sequencial
- [ ] Pix confirmado só pelo servidor: webhook verificado + consulta ao provedor
- [ ] Chaves do provedor só no servidor; nunca no navegador, no Git ou nos logs
- [ ] Dados pessoais mínimos; mascarados nas telas; prazo para apagar (LGPD)
- [ ] Cabeçalhos de segurança (CSP e outros)
- [ ] Backups fora do servidor com restauração testada
- [ ] Atualizações de segurança em dia; alertas configurados
- [ ] Varredura de vulnerabilidades antes de abrir ao público
- [ ] Botão de emergência para desligar pedidos online

### 9.5 Testes mais importantes da Fase 2

- Preço alterado no navegador é ignorado; total vem do servidor
- Produto esgota com o item no carrinho: pedido recusado, carrinho preservado
- Duas pessoas pedem a última unidade ao mesmo tempo: só uma consegue
- Clique duplo no "finalizar" gera um pedido só
- Link de acompanhamento de um pedido não abre outro pedido
- Webhook falsificado é recusado; webhook repetido não duplica
- Pix pago depois da expiração ou do cancelamento é detectado
- Devolução repetida ou acima do pago é recusada
- Pastelaria fechada recusa pedido no servidor, mesmo chamando a API direto
- Rotas internas continuam fechadas para quem não tem login

### 9.6 Custos (a pesquisar, sem chutar números)

- Servidor (mensal) e domínio (anual)
- Taxa do provedor por Pix recebido (e por devolução, se houver)
- Tempo da família para acompanhar a fila de pedidos

### 9.7 Cuidados e decisões que ainda faltam

| Assunto | O que fazer |
|---|---|
| **Plano B do balcão** | combinar com a família: se o sistema ou a internet cair no meio do movimento, anotar as vendas no papel e lançar depois |
| **Fornecedor da VPS** | pesquisar e comparar (preço, servidor no Brasil, backup, facilidade); decidir com dados, não por propaganda |
| **Dados de clientes (LGPD)** | as Etapas 13 a 16 rodam só no **ambiente de testes**, sem dados reais; política de privacidade e prazo para apagar dados ficam prontos **antes** de coletar dado de cliente real |
| **Como avisar o cliente** | nas Etapas 13 a 16 (Pix expirado, item que acabou, devolução), definir **por onde** o cliente fica sabendo: só pela página de acompanhamento ou também por WhatsApp |
| **Exigências do negócio** | o provedor de Pix pede CPF ou CNPJ; verificar com um contador as obrigações de vender online (MEI, nota fiscal). Não é decisão técnica |
