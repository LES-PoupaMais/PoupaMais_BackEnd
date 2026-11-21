package br.com.poupamais.shared.exception;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Código estável de cada resposta de erro (campo {@code codigo} do {@link ErroResponse}). O front
 * decide o que fazer pelo código; a {@code mensagem} continua sendo o texto pronto para exibir
 * (RNF007). Código novo entra aqui, na tabela do CLAUDE.md e em pelo menos um teste. Nunca renomeie
 * um código existente: é contrato.
 */
@Schema(description = "Código estável do erro (MAIÚSCULAS_COM_UNDERSCORE). Tabela no CLAUDE.md.")
public enum CodigoErro {

  // Genéricos -----------------------------------------------------------------------------------
  VALIDACAO(400, "Campos inválidos no corpo ou nos parâmetros (vem com erros[])"),
  CORPO_INVALIDO(400, "Corpo ilegível: JSON quebrado, valor fora do enum, tipo errado"),
  REQUISICAO_INVALIDA(400, "Requisição recusada antes de chegar à API (ex.: URL malformada)"),
  PARAMETRO_INVALIDO(
      400, "Parâmetro de rota, query ou header com tipo errado (ex.: id não numérico)"),
  PARAMETRO_AUSENTE(400, "Parâmetro, part de multipart ou header obrigatório ausente"),
  NAO_AUTENTICADO(401, "Sem token, token inválido/expirado ou sessão derrubada"),
  ACESSO_NEGADO(403, "Usuário autenticado sem permissão para o recurso"),
  NAO_ENCONTRADO(404, "Rota inexistente"),
  METODO_NAO_PERMITIDO(405, "Método HTTP não suportado na rota"),
  ARQUIVO_GRANDE_DEMAIS(
      422, "Upload acima de 2 MB (CSV, foto ou multipart); 413 se o servidor recusar antes"),
  TIPO_RESPOSTA_NAO_ACEITO(406, "Accept sem nenhum formato que a rota produz (resposta em JSON)"),
  TIPO_CONTEUDO_NAO_SUPORTADO(415, "Content-Type não aceito pela rota"),
  ERRO_INTERNO(500, "Erro inesperado (sem detalhes internos)"),

  // Conta (usuario) -----------------------------------------------------------------------------
  CREDENCIAIS_INVALIDAS(401, "Login com e-mail inexistente ou senha errada"),
  EMAIL_JA_CADASTRADO(409, "Cadastro com e-mail que já existe"),
  ACEITE_TERMOS_OBRIGATORIO(422, "Cadastro sem aceitar os termos (regra no service)"),
  DADOS_CONTA_INVALIDOS(422, "Nome ou senha fora das regras (regra no service)"),
  SENHA_ATUAL_INCORRETA(422, "Troca de senha com a senha atual errada"),
  SENHA_IGUAL_A_ATUAL(422, "Troca de senha com a nova igual à atual"),
  SENHA_INCORRETA(422, "Exclusão de conta com a senha errada"),
  TOKEN_INVALIDO(422, "Link de redefinição de senha inválido, expirado ou já usado"),
  FOTO_TIPO_INVALIDO(422, "Foto que não é JPG, PNG ou WEBP (ou sem o campo foto)"),
  FOTO_NAO_ENCONTRADA(404, "Usuário sem foto de perfil"),

  // Categoria -----------------------------------------------------------------------------------
  CATEGORIA_NAO_ENCONTRADA(404, "Categoria inexistente ou de outro usuário"),
  CATEGORIA_DUPLICADA(
      409, "Já existe categoria com a mesma descrição (sem diferenciar maiúsculas)"),
  CATEGORIA_INVALIDA(422, "Descrição da categoria vazia ou longa demais (regra no service)"),
  CATEGORIA_INATIVA(422, "Categoria inativa usada em lançamento"),
  CATEGORIA_VINCULADA(422, "Exclusão de categoria que tem lançamentos"),

  // Lançamento ----------------------------------------------------------------------------------
  LANCAMENTO_NAO_ENCONTRADO(404, "Lançamento inexistente ou de outro usuário"),
  LANCAMENTO_INVALIDO(422, "Dados do lançamento fora da RN001 (ex.: data fora de 2000–2100)"),
  LANCAMENTO_NA_LIXEIRA(422, "Editar ou trocar o status de um lançamento DELETADO"),
  LANCAMENTO_JA_EXCLUIDO(422, "DELETE de lançamento que já está na lixeira"),
  LANCAMENTO_NAO_EXCLUIDO(422, "Restaurar lançamento que não está na lixeira"),
  STATUS_INVALIDO(422, "Status DELETADO ou ausente onde só cabe ATIVO/INATIVO"),
  TRANSICAO_STATUS_INVALIDA(422, "Transição de status não permitida pela RN006"),

  // Arquivo (importação/exportação) -------------------------------------------------------------
  ARQUIVO_VAZIO(422, "Importação sem arquivo, com arquivo vazio ou sem linhas de dados"),
  ARQUIVO_INVALIDO(422, "Arquivo que não é texto UTF-8"),
  CABECALHO_INVALIDO(422, "CSV sem o cabeçalho ou com colunas faltando"),
  ARQUIVO_LINHAS_DEMAIS(422, "CSV com mais de 5.000 linhas de dados"),
  FORMATO_EXPORTACAO_INVALIDO(400, "Exportação com formato diferente de csv/xlsx"),
  SEM_DADOS_EXPORTACAO(422, "Exportação sem lançamentos no filtro");

  private final int status;
  private final String quando;

  CodigoErro(int status, String quando) {
    this.status = status;
    this.quando = quando;
  }

  /** Status HTTP com que o código costuma sair (documentação; quem responde é o handler). */
  public int status() {
    return status;
  }

  /** Quando o código acontece (documentação: tabela do Swagger). */
  public String quando() {
    return quando;
  }

  /**
   * Código padrão de um status HTTP sem exceção de negócio (ex.: erro do servidor de aplicação).
   */
  public static CodigoErro padraoDoStatus(int status) {
    return switch (status) {
      case 400 -> REQUISICAO_INVALIDA;
      case 401 -> NAO_AUTENTICADO;
      case 403 -> ACESSO_NEGADO;
      case 404 -> NAO_ENCONTRADO;
      case 405 -> METODO_NAO_PERMITIDO;
      case 413 -> ARQUIVO_GRANDE_DEMAIS;
      case 415 -> TIPO_CONTEUDO_NAO_SUPORTADO;
      default -> status >= 400 && status < 500 ? REQUISICAO_INVALIDA : ERRO_INTERNO;
    };
  }
}
