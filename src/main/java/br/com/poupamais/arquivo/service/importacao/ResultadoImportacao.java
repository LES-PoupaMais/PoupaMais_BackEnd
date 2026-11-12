package br.com.poupamais.arquivo.service.importacao;

import java.util.List;

/**
 * Resposta da importação (RN007).
 *
 * @param totalLinhas linhas de dados não vazias lidas (sem cabeçalho e sem linhas em branco)
 * @param importados lançamentos gravados
 * @param falhas linhas recusadas, com o número real no arquivo (cabeçalho = 1)
 * @param categoriasCriadas descrições das categorias criadas automaticamente (RN002)
 */
public record ResultadoImportacao(
    int totalLinhas, int importados, List<Falha> falhas, List<String> categoriasCriadas) {

  public record Falha(int linha, String conteudo, String motivo) {}
}
