package br.com.poupamais.arquivo.service.exportacao;

/** Arquivo pronto para download. */
public record ArquivoExportado(String nome, String contentType, byte[] conteudo) {}
