# Identidade visual — Pastelaria do Galo

> **Nomes:**
> - **Pastelaria do Galo**: o nome da pastelaria (a marca que o cliente conhece).
> - **Galo & Cia**: o nome do **sistema** que reúne a pastelaria e a peixaria.
> - "Pastelaria SS" era um nome provisório e não deve mais aparecer nos textos
>   visíveis (telas, README, plano).
>
> Nomes técnicos que **não** mudam, de propósito: o pacote Java
> (`br.com.pastelariass`) e o nome do projeto do Docker (`pastelaria-ss`).
> Trocar o do Docker faria o sistema procurar um volume novo e vazio, e os dados
> existentes pareceriam ter sumido.

Fonte: perfil do Instagram `@pastelaria_do_galo` e a arte do cardápio.

## Paleta de cores

Cores **aproximadas**, tiradas a olho da arte (não medidas em pixel). Se houver
o arquivo original da logo, confirmar os códigos exatos.

| Papel | Cor | Código aproximado | Onde aparece na marca |
|---|---|---|---|
| Principal | vermelho-vinho | `#B3191E` | fundo do cardápio |
| Destaque | amarelo-ouro | `#F6C21B` | faixas dos títulos, fundo da logo |
| Apoio | laranja | `#E8761E` | crista do galo, "do GALO" |
| Texto escuro | marrom quase preto | `#2B1A12` | contornos da logo |
| Fundo claro | creme | `#FFF4D6` | caixas de texto do cardápio |

Sugestão de uso como variáveis CSS (Bootstrap 5):

```css
:root {
  --galo-vermelho: #B3191E;
  --galo-amarelo:  #F6C21B;
  --galo-laranja:  #E8761E;
  --galo-escuro:   #2B1A12;
  --galo-creme:    #FFF4D6;

  --bs-primary: var(--galo-vermelho);
  --bs-warning: var(--galo-amarelo);
}
```

### Regras de contraste (legibilidade)

- **Sobre o amarelo e o creme:** texto escuro (`#2B1A12`). Texto branco no amarelo fica ilegível.
- **Sobre o vermelho e o marrom:** texto branco.
- Não usar só a cor para indicar estado (ex.: pago/pendente): sempre junto com texto ou ícone.
- Conferir a tela no celular, inclusive com brilho alto (uso ao ar livre).

## Elementos da marca

- Mascote: um **galo** colorido, com pastéis.
- Logo: círculo amarelo com "PASTELARIA DO GALO" em letras vermelhas/laranja com contorno escuro.
- Tom do cardápio: chamativo e alegre ("Sabor que sempre te chama", "Venha nos visitar!").

## Funcionamento (do Instagram)

- Local: **Lagoa de Praia** (S/N, próximo à NF Bebidas).
- Aberto de **quinta a domingo, a partir das 18h**.
- Faz **delivery**; pedidos via **WhatsApp** (83) 99362-5171.

## Cardápio (transcrito da arte)

⚠️ Transcrito de uma imagem pequena: **conferir cada item e preço com a família**
antes de usar como cadastro inicial de produtos.

### Pastéis salgados
| Produto | Preço |
|---|---|
| Carne | R$ 8,00 |
| Queijo | R$ 8,00 |
| Frango | R$ 8,00 |
| Camarão | R$ 11,00 |
| Carne de sol | R$ 11,00 |

### Pastéis especiais
| Produto | Preço |
|---|---|
| Carne de sol + queijo | R$ 13,00 |
| Carne de sol + catupiry | R$ 13,00 |
| Frango + queijo | R$ 9,00 |
| Frango + catupiry | R$ 9,00 |
| Carne + queijo | R$ 9,00 |
| Carne + catupiry | R$ 9,00 |
| Camarão + catupiry | R$ 15,00 |
| Camarão + queijo | R$ 15,00 |
| 2 queijos | R$ 9,00 |
| Misto | R$ 9,00 |

### Pastéis doces
| Produto | Preço |
|---|---|
| Banana caramelizada com nutella | R$ 12,00 |
| Romeu e Julieta | R$ 12,00 |

### Bebidas
| Produto | Preço |
|---|---|
| Coca-Cola zero | R$ 6,00 |
| Coca-Cola lata | R$ 6,00 |
| Coca-Cola 1 litro | R$ 10,00 |
| Suco de maracujá 300 ml | R$ 4,00 |
| Suco de maracujá 500 ml | R$ 5,00 |
| Caldo de cana 300 ml | R$ 3,00 |
| Caldo de cana 500 ml | R$ 4,00 |
