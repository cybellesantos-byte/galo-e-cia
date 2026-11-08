# Plano da melhoria visual (antes da Fase 2)

Objetivo: deixar as telas **bonitas, organizadas e rápidas de usar no balcão**,
no PC e no celular. Só a parte da frente (`static/`). O backend e as regras de
dinheiro não mudam.

Por que agora: o cardápio público da Fase 2 vai reaproveitar estes mesmos
componentes (lista de produtos por categoria, busca, carrinho). Arrumar aqui
evita refazer duas vezes.

---

## 1. Diagnóstico (fotos de 29/10/2026, com 32 pastéis e 6 bebidas)

| Problema | Onde | Por que incomoda |
|---|---|---|
| Parede de quadrados iguais, pastel e bebida misturados, em ordem alfabética | Vender | Com 30+ sabores, achar "Frango com catupiry" é procurar agulha |
| Um campo de número por produto (38 caixinhas com "0") | Vender | Poluição visual; digitar quantidade é lento no toque |
| No celular, o pedido e o botão "Registrar venda" ficam depois de rolar a lista inteira | Vender | Quem vende não vê o total enquanto escolhe |
| Lista enorme, 3 botões por produto, sem busca nem grupos | Produtos | Tela de 4 mil pixels de altura |
| Menu com 9 botões no topo; no celular ocupa 3 linhas | Todas | Toma metade da tela do celular |
| Cores: creme + vermelho em tudo, sem hierarquia; o verde do botão "Registrar" destoa | Todas | Nada se destaca, tudo grita igual |
| Só a marca da pastelaria; a peixaria não tem identidade | Topo, login | O sistema é dos dois negócios |

**Sobre as dúvidas:**
- **O Bootstrap está sendo usado** (`static/vendor/bootstrap`). O problema é que usei
  os componentes "crus" e pintei por cima com CSS. Faltou um **sistema de design**:
  regras fixas de espaço, tamanho, cor e componentes.
- **O JavaScript está funcionando.** É ele que desenha cada tela, chama a API e
  calcula o troco. O que está ruim é a **organização visual** que eu escolhi, não
  a tecnologia.

---

## 2. Princípios (valem para todas as telas)

1. **A tela de venda é a mais importante.** Ela é usada o tempo todo, com pressa, em pé.
2. **Achar em até 2 toques ou 3 letras:** busca + categorias.
3. **O total sempre visível** enquanto se monta o pedido.
4. **Toque confortável:** tudo que se toca tem pelo menos 44 px.
5. **Uma cor de destaque por tela:** vermelho só para a ação principal; o resto, neutro.
6. **Cada negócio com a sua cara:** o galo na pastelaria e a marca da peixaria na
   peixaria, para nunca vender no negócio errado.
7. **Sentido nunca só pela cor:** sempre texto ou ícone junto.

---

## 3. Fases

Cada fase: protótipo → você aprova → implemento → teste de tela (porta 8099,
banco descartável) com fotos de PC e celular → sabotagem → commit com a sua
aprovação. Uma branch: `etapa-10b/visual`.

### V0 — Protótipo para escolher o visual ✅ (29/10/2026)
- Protótipo com 6 estilos, todos funcionando (busca, + e −, pedido).
- **Escolhido: estilo B, "Cardápio em grade"**, no PC e no celular:
  - topo vermelho com faixa amarela, fontes Baloo 2 (títulos) e Nunito (texto), fundo creme;
  - cartões grandes em grade (2 colunas no celular): tocar soma 1; o escolhido ganha
    borda vermelha, o número no canto e um botão "− tirar um";
  - uma seção por categoria, com atalhos que rolam para o lado e levam à seção;
  - PC: pedido sempre visível ao lado. Celular: barra vermelha "Ver pedido · total"
    embaixo, e o pedido sobe de baixo para cima para pagar.
- **Organização dos pastéis (ideia da dona):** Simples · Com queijo · Com Catupiry ·
  Com cheddar · Doces · Bebidas. As bebidas ficam separadas na tela, mas entram no
  mesmo pedido.
  - No sistema, isso usa o campo "categoria" que o produto já tem. A ordem das seções
    segue essa lista; uma categoria nova que não esteja nela entra antes de Bebidas,
    em ordem alfabética.

### V1 — Base visual (o "sistema de design") ✅ (29/10/2026, falta só a logo)
Feito:
- cores e fontes do estilo B (Baloo 2 e Nunito salvas em `static/vendor/fonts`, licença OFL);
- botões, campos e cartões padronizados; as opções escolhidas ficam amarelas, e o vermelho
  fica para a ação principal; o "Registrar venda" deixou de ser verde;
- menu novo:
  - PC: no topo, com ícones e o negócio sempre à vista;
  - celular: barra embaixo (Vender, Caixa, Despesas, Mais) e painel "Mais";
- ícones desenhados em SVG dentro do próprio `index.html`, sem baixar biblioteca.

As logos entraram na V5.

Plano original:
- Paleta revisada: fundo mais limpo (branco/cinza bem claro), vermelho só no que importa,
  amarelo como detalhe; e uma **cor da peixaria** (a definir com a logo dela).
- Tipografia e espaçamentos em escala fixa (variáveis CSS no `:root`).
- Componentes padrão: botão, cartão, campo, selo (badge), lista, cabeçalho de tela,
  estado vazio ("nenhum produto ainda") e carregando.
- **Ícones:** Bootstrap Icons, salvos no projeto, sem buscar na internet.
- **Logos:** galo e peixaria em `static/img/`; topo e login mostram a do negócio escolhido.
- **Menu:**
  - PC: barra lateral com ícone e nome;
  - celular: barra embaixo com Vender, Caixa, Despesas e **Mais** (o resto abre num painel).
- Contraste conferido (texto ≥ 4,5:1).

### V2 — Tela de Vender (a principal) ✅ (30/10/2026)
Feito como no protótipo B:
- cartões por categoria; tocar soma 1, com "− tirar um" embaixo;
- busca que ignora acento; atalhos por seção;
- pedido ao lado no PC, que rola por dentro se for alto;
- no celular, barra "Ver pedido" e painel que sobe;
- na peixaria, peso digitado com vírgula.

Os campos de número do sistema inteiro perderam as setinhas. Testado com 49 produtos.

Plano original:
- **Busca** no topo: digitar "cat" já mostra "Frango com catupiry". Ignora acentos e
  maiúsculas.
- **Abas por categoria:** **Pastéis | Bebidas | (outras)**. As categorias vêm do campo
  "categoria" do produto, que já existe; não muda o banco.
  - Ordem fixa: Pastéis primeiro, Bebidas por último, as outras no meio.
  - Bebidas ficam separadas na tela, mas entram **no mesmo pedido**.
- **Lista compacta:** uma linha por produto, com nome, preço e botões **− / +**. A
  quantidade aparece só quando é maior que zero.
  - Tocar no nome soma 1.
  - Na peixaria, o kg abre um campo com teclado numérico.
- **Pedido como carrinho:**
  - PC: coluna fixa à direita;
  - celular: barra fixa embaixo ("3 itens · R$ 27,00 · Ver pedido"), que abre o
    pedido completo para pagar.
- O botão "Registrar venda" na cor da marca, e não verde.
- *Ideia futura, fora deste plano:* "mais vendidos" no topo da lista. Usaria dados que
  o relatório já calcula.

### V3 — Produtos ✅ (30/10/2026)
Feito:
- busca e filtro por situação (Todos, Para venda, Esgotados, Fora da lista);
- grupos por categoria, na mesma ordem da venda;
- linha compacta, com as ações no "⋯";
- formulário num painel lateral;
- categoria com botões de sugestão e grafia corrigida sozinha ("bebidas" vira "Bebidas");
- preço com vírgula.

A regra das categorias foi para `static/js/categorias.js`, usada pela Venda e por Produtos.

Plano original:
- Mesma busca e mesmos grupos por categoria da tela de venda.
- Linha compacta: nome, preço, situação (Para venda / Esgotado / Fora da lista) e um
  botão **"⋯"** com Editar, Esgotar e Tirar da lista.
- Formulário de novo produto num painel lateral (abre e fecha), e não fixo na tela.
- Categoria com **sugestões** das que já existem. Evita ter "Bebida", "bebidas" e
  "Bebidas" ao mesmo tempo.

### V4 — Demais telas ✅ (30/10/2026)
Feito (a V1 já tinha mudado quase tudo; aqui foi o acabamento):
- períodos e abas dos Relatórios em comprimidos numa linha só; no celular, a linha rola para o lado;
- valores dos resumos na fonte de título;
- título de "Trocar negócio" no padrão das outras telas;
- "Atualizar" do Caixa sempre no canto.

Plano original:
Caixa, Despesas, Repasses, Relatórios, Equipe, Minha senha e Trocar negócio: aplicar a
base da V1. Elas já estão organizadas; o trabalho é deixá-las com a mesma cara.

### V5 — Login e acabamento ✅ (30/10/2026, logos)
Feito:
- **Galo:** `static/img/logo-galo.png`, a imagem enviada pela dona (140 × 130 px). Os cantos escuros
  da imagem foram deixados **transparentes** (nada dentro do círculo foi alterado). O original
  fica guardado fora do projeto.
- **Peixaria:** `static/img/logo-peixaria.svg`, uma logo **provisória** desenhada por nós (peixe amarelo
  em círculo azul-petróleo). Pode ser trocada pela oficial, se houver, com o mesmo nome de arquivo.
- **Login:** mostra as duas logos lado a lado.
- **Topo:** mostra a logo do negócio escolhido, e ela muda junto com o selo amarelo.
- A logo da aba do navegador (favicon) é o galo.
- Backend: `/img/**` liberado sem login (o login precisa das logos antes de qualquer sessão), com teste.

Melhoria futura: um arquivo maior da logo do galo (o atual tem poucos pixels e pode ficar um pouco
borrado em telas de alta resolução) e a logo oficial da peixaria.

Falta da V5 (conferência final):
- Conferência final:
  - fotos de todas as telas no PC e no celular;
  - navegação só por teclado;
  - leitor de tela nos pontos principais;
  - nada passando da borda.

---

### V6 — Cara própria da peixaria ✅ (30/10/2026, ideia da dona)
Ao escolher a peixaria, a paleta do sistema inteiro muda, para ficar claro de longe em qual
negócio se está:
- topo e botões principais em azul-marinho;
- faixa, selo e item escolhido em azul-água;
- fundo azul bem clarinho.

A pastelaria continua vermelha e amarela, e o login (do Galo & Cia) é sempre vermelho.

Como funciona: o `app.js` marca o negócio na página (`<body data-negocio="PEIXARIA">`), e o
`app.css` redefine as variáveis `--galo-...` para esse caso. Nenhuma tela foi alterada uma a uma.

Contrastes conferidos, todos acima de 4,5:1:
- texto escuro no fundo: 14,8:1;
- texto de apoio: 6,5:1;
- branco no azul-marinho: 9,1:1;
- texto escuro no azul-água: 9,4:1;
- "& Cia" no topo: 5,3:1.

## 4. Testes

- Os scripts de tela atuais procuram elementos que vão mudar (ex.: `.produto-quantidade`).
  Eles serão **atualizados junto** com cada tela.
- Teste novo da venda com 32 pastéis + 6 bebidas:
  - a busca acha;
  - as bebidas aparecem em aba separada;
  - o − / + soma certo;
  - o total e o troco batem;
  - o pedido aparece sem rolar, no celular.
- Sabotagem em cada fase (ex.: busca que não ignora acento; bebida caindo na aba de pastéis).
- Os 226 testes do backend continuam passando. Eles não devem ser afetados.

---

## 5. Decisões que dependem de você

1. **Logos:** mandar os arquivos (PNG ou SVG) do galo e da peixaria. A peixaria já tem
   logo e nome próprio?
2. **Cor da peixaria:** sugestão é um azul-marinho ou verde-água, que combine com o
   vermelho e o amarelo do galo. Melhor decidir vendo a logo.
3. **Estilo:** escolhido no protótipo da V0.
4. **Categorias:** além de Pastéis e Bebidas, existem outras (porções, doces, combos)?

## 6. Fora deste plano

Pedidos online, cardápio público, Pix e VPS ficam na Fase 2 (`docs/PLANO.md`, seção 9).
