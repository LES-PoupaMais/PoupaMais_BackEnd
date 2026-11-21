# Ideias para a evolução do PoupaMais (a discutir com a equipe)

> **Status: ideias em aberto, nada aqui está aprovado nem deve ser implementado sem decisão da equipe.**
> Levantadas em 12/11/2026 com base em projetos acadêmicos antigos de finanças pessoais, como ponto de partida para discutir
> **como** o PoupaMais pode crescer depois da entrega do escopo atual.
> O mesmo arquivo existe nos dois repositórios (`PoupaMais_BackEnd/doc/` e `PoupaMais_FrontEnd/doc/`): mude os dois juntos.
> Revisado em 20/11/2026: o que já foi feito saiu da lista e está na seção 7.

## 1. Diretrizes já combinadas

1. **Escopo atual = `CLAUDE.md` de cada repositório.** Os `.md` em `../Documentação/md/` (DVP v1.4, apresentação, Trello) são uma
   versão anterior; quando divergirem, vale o `CLAUDE.md`.
2. **Primeiro o escopo atual é concluído** (back e front já estão). Só depois a equipe decide se e como o escopo cresce. Se crescer,
   **os requisitos (RF/RN/RNI) serão reescritos** e os `CLAUDE.md` atualizados antes de qualquer código.
3. **O design foi refeito em conjunto**, mobile-first e responsivo para tablet e computador: o Design System e as pranchetas das
   telas são a referência visual (ver o `CLAUDE.md` do front).
4. **Ideias de outros projetos nunca entram como cópia de código.** Toda funcionalidade é escrita do nosso jeito:
   - regra de negócio, cálculo e persistência **no back** (monólito modular com Spring Modulith, MVC em camadas, Facade, DTO + Mapper,
     Specification, Strategy, Validator, Value Object, conforme o `CLAUDE.md` do back);
   - **no front** só apresentação e interação (Next.js App Router, Atomic Design com shadcn/ui, BFF, telas em `screens/` e a cadeia
     `lib/http → services → hooks → Context da tela`, que passa os dados aos organismos por props).

## 2. Lições dos projetos acadêmicos antigos (o que não repetir)

- Regra de negócio espalhada entre funções SQL, código do navegador e as próprias telas.
- Telas acessando o banco diretamente, sem camada de serviço nem contrato de API.
- Um componente raiz gigante concentrando dados, rotas e sessão; páginas com mais de mil linhas.
- CSS global em camadas sobrepostas, sem componentes reutilizáveis com variantes.
- Segurança dependente só de regras do banco, com o front confiando demais no cliente.

No PoupaMais: cada regra vira **um** service no módulo certo do back, exposta por endpoint documentado no contrato; o front só consome pelo BFF.

## 3. Ideias de linguagem visual (já usadas na discussão do design)

> Esta seção serviu de ponto de partida para o Design System, que já existe e é quem manda. Fica aqui só como histórico.

Referência de um estilo amigável e profissional, levada para a discussão com a equipe:

- Fundo levemente lavanda, cartões brancos com sombra suave, cantos bem arredondados (18–24px), botões e chips em pílula.
- Indigo como ação principal (um botão preenchido por tela); coral para alertas e exclusão; verde para positivo/receita; amarelo para destaque.
- Uma única fonte sans moderna (ex.: Plus Jakarta Sans) com hierarquia por peso (500–800); rótulos de seção pequenos em caixa alta.
- Alvos de toque ≥ 44px, campos de 48px, foco visível com anel de 2px; modo claro e escuro com as mesmas formas.
- No celular: barra inferior com botão "+" central, folha inferior "Criar", seletor de mês no topo, sino de avisos. No desktop: navegação lateral ou superior.
- Padrões: cartão-resumo de destaque, seletor segmentado, linha de lançamento com ícone em quadrado colorido, faixas de aviso/ok, estados vazio/erro/carregando.

Como ficou no front: tokens do Design System nas variáveis do shadcn em `src/styles/globals.css`, variantes por `cva` nos átomos,
fonte por `next/font`.

## 4. Funcionalidades candidatas (fora do escopo atual)

| #   | Ideia                                                                                                                   | Observação                                                      |
| --- | ----------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------- |
| 1   | Cartões de crédito (vencimento, melhor dia de compra, em que fatura a compra cai)                                       | Hoje o lançamento tem só a data (sem fatura nem vencimento)     |
| 2   | Faturas: fechamento, situação (aberta, paga, parcial, pendente para o mês seguinte), pagar fatura, alerta de vencimento |                                                                 |
| 3   | Compras parceladas e antecipação de parcelas                                                                            |                                                                 |
| 4   | Contas bancárias e forma de pagamento (cartão, PIX, boleto)                                                             |                                                                 |
| 5   | Importar fatura em PDF por banco e conferência fatura × sistema                                                         | Amplia o módulo `arquivo`                                       |
| 6   | Dividir despesas com outras pessoas (igual, por %, por R$) com regra de arredondamento                                  | Mudança grande de domínio                                       |
| 7   | Cobrança dessas pessoas: saldo, pagamentos, crédito, mensagem pronta para envio                                         |                                                                 |
| 8   | Acesso de "observador" (a pessoa vê só a parte dela, envia comprovante e comenta)                                       | Novo perfil de acesso                                           |
| 9   | Compra detalhada por itens (ex.: cupom de mercado)                                                                      |                                                                 |
| 10  | Estorno e suspensão de compra (contestação)                                                                             |                                                                 |
| 11  | Recorrentes: contas fixas (previsão pela média) e assinaturas                                                           |                                                                 |
| 12  | Previsão de caixa dos meses futuros                                                                                     | Dashboard atual só olha o passado                               |
| 13  | Orçamento por categoria e regra 50/30/20                                                                                |                                                                 |
| 14  | Receitas recebidas e acompanhamento de investimentos                                                                    | ⚠ Avaliar contra **RNI003**                                     |
| 15  | Central de avisos com filtros                                                                                           |                                                                 |
| 16  | Histórico/auditoria de alterações e importações                                                                         | Hoje só as datas de criação, alteração e exclusão do lançamento |
| 17  | Open Finance com revisão antes de virar lançamento                                                                      | ⚠ Conflita com **RNI002**                                       |
| 18  | Backup automático e aviso de backup antigo                                                                              | Atende RNF005                                                   |

## 5. Encaixe na arquitetura (rascunho)

**Back:** possíveis novos módulos, cada um com Facade pública e `api/ service/ model/` internos: `cartao` (faturas, parcelas),
`pessoa` (divisão), `cobranca`, `recorrente`, `previsao`/`orcamento`, `aviso` (alertas com `@Scheduled` + `notificacao`), ampliação de `arquivo`
(Strategy + Factory por banco). Dinheiro continua em `BigDecimal`, com a invariante "soma das partes = total".

**Front:** cada tela nova vira rota limpa no App Router + pasta em `screens/` (Context com os hooks de dados e View que monta os
organismos puros); padrões visuais viram átomos/moléculas com variantes. Nenhum componente calcula regra de negócio: tudo vem
pronto da API.

## 6. Em aberto

1. Quais ideias entram e em que ordem.
2. RNI001, RNI002 e RNI003: manter fora ou revisar.
3. Status DELETADO/lixeira × suspensão: unificar o modelo de status.

## 7. Já feito (saiu da lista)

- **Novo design** (antes item 3 de "Em aberto"): Design System e pranchetas das telas, com navegação própria para celular (barra
  inferior com "+" e folha "Criar"), tablet (trilho) e computador (barra lateral).
- **PWA instalável com consulta offline** (antes na lista de candidatas; pedido pelo líder em 19/11/2026): o front instala como
  app e, sem internet, mostra os últimos dados guardados, só para consulta. Continua sendo web, sem app nativo (**RNI001**).
