package br.com.poupamais.arquivo.service.importacao;

import java.util.Map;

/**
 * Uma linha de dados do CSV já separada em colunas.
 *
 * @param numero número real da linha no arquivo (cabeçalho = 1)
 * @param conteudo texto original da linha (devolvido nas falhas)
 * @param campos valor de cada coluna obrigatória pelo nome; vazio se {@code erro} não for nulo
 * @param erro motivo quando a linha não pôde ser separada (colunas faltando/sobrando, aspas)
 */
public record LinhaCsv(int numero, String conteudo, Map<String, String> campos, String erro) {}
