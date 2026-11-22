# Importação e exportação: formato CSV

> Detalhe do guia (antes no `CLAUDE.md`). Índice no `AGENTS.md`.

## Formato CSV (RN012 no DVP)

- Separador `;` (abre direto no Excel pt-BR), UTF-8 (aceitar BOM), 1ª linha = cabeçalho.
- Cabeçalho: `descricao;tipo;data;valor;categorias` (desde 18/11/2026; antes `descricao;tipo;mes;ano;valor;categorias`)
- `data`: `dd/mm/aaaa` **estrito** (dia e mês com 2 dígitos, ano com 4, dia que exista no mês, entre 01/01/2000 e 31/12/2100);
  `tipo`: `RECEITA`/`DESPESA` (sem diferenciar maiúsc./minúsc.); `valor`: `1234,56` ou `1234.56`;
  `categorias`: várias separadas por `|` (ex.: `Alimentação|Lazer`).
- Exemplo:
  ```
  descricao;tipo;data;valor;categorias
  Salário mensal;RECEITA;05/08/2026;8500,00;Salário
  iFood — pedidos do mês;DESPESA;12/08/2026;420,00;Alimentação|Lazer
  ```
- Limites: **2 MB** (`spring.servlet.multipart.max-file-size=2MB`) e **5.000 linhas de dados**. Excedeu ou sem cabeçalho → 422 com o arquivo inteiro recusado.
- Cada linha passa pelo mesmo `LancamentoValidator` (RN001). O método de importação **não** é `@Transactional`:
  cada linha válida é salva na sua própria transação (categorias novas + `LancamentoFacade.criar`), então uma falha não desfaz as outras (RN007).
- A exportação usa o **mesmo layout** (+ coluna `status`), para que um arquivo exportado possa ser reimportado.

Decisões da implementação (`feat/arquivo`):
- **Cabeçalho**: localizado pelo nome (trim, sem diferenciar maiúsc.), em qualquer ordem; as 5 colunas são obrigatórias e colunas
  extras (ex.: `status` do arquivo exportado) são ignoradas. Linhas em branco antes do cabeçalho são puladas.
- **Recusa do arquivo inteiro (422, nada é gravado)**: vazio/sem campo `arquivo`, > 2 MB, não é texto UTF-8 válido (ou tem byte nulo),
  cabeçalho ausente/incompleto, nenhuma linha de dados, > 5.000 linhas de dados não vazias.
- **Linha em branco**: ignorada; **não** conta em `totalLinhas` nem como falha. `totalLinhas` = linhas de dados não vazias.
  `linha` = número real no arquivo (cabeçalho = 1, linhas em branco contam na numeração).
- Linha com colunas faltando/sobrando ou aspas quebradas → falha da linha (campos com `;` vão entre aspas duplas; uma linha = um registro).
- **Valor**: aceita `1234,56`, `1234.56`, `1.234,56`, `1,234.56`, `R$ 10,5`. Com vírgula **e** ponto, o último é o decimal e o outro é milhar;
  com só um deles, ele é o decimal, salvo se repetido (`1.234.567` = milhar). Máximo 2 casas (`1,234` e `10,555` são falha). Negativo/zero → falha.
- **Data**: vazia → "A data é obrigatória (dd/mm/aaaa)"; dia que não existe ou fora do formato → "Data inválida (31/02/2026). Use dd/mm/aaaa
  com um dia que exista." (mostra o valor digitado); fora da faixa → "A data deve estar entre 01/01/2000 e 31/12/2100".
- `tipo`/`data`/`descricao`/categorias são checados **antes** de criar qualquer categoria (checagem prévia da RN001 no `ConversorLinha`);
  a validação definitiva continua no `LancamentoValidator` via `LancamentoFacade.criar`. Categorias repetidas na linha (ignorando maiúsc.) contam uma vez.
- **Categoria inativa** citada → falha da linha "A categoria "X" está inativa e não pode ser usada", sem criar as outras categorias novas da linha.
  Categoria nova (RN002) só é criada (`buscarOuCriar`) depois que a linha passou em tudo isso; `categoriasCriadas` lista as descrições criadas.
- **Transação por linha** (pendência 12): o `ImportacaoService` abre uma transação só da linha (`TransactionOperations`) com a criação
  das categorias novas e o `LancamentoFacade.criar`; as facades usam a propagação padrão (`REQUIRED`) e entram nela. Se `criar` recusar
  a linha (ou o commit falhar), a categoria criada para ela é desfeita junto: nenhuma categoria órfã, e `categoriasCriadas` (e o cache
  da importação) só recebem categorias de linhas **gravadas**.
- Erro inesperado numa linha → falha com "Não foi possível salvar esta linha. Tente novamente." (só o tipo da exceção vai para o log).
- **Exportação**: CSV em UTF-8 **com BOM**, `;`, CRLF, data `dd/mm/aaaa`, valor `8500,00`; XLSX (aba "Lançamentos", cabeçalho em negrito,
  `data` como **célula de data** com formato `dd/mm/yyyy`, valor numérico com formato `0.00`). Nome `lancamentos-AAAA-MM-DD.<formato>` (data de São Paulo). `formato` ausente ou diferente de `csv`/`xlsx` → 400
  "Formato inválido. Use: csv ou xlsx". Content types: `text/csv;charset=UTF-8` e
  `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`. Template baixa como `template.csv` (`text/csv;charset=UTF-8`).
