package br.com.poupamais.arquivo.service.importacao;

import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.List;
import org.apache.commons.csv.CSVFormat;

/** Layout do CSV de lançamentos (RN012), compartilhado pela importação e pela exportação. */
public final class LayoutCsv {

  public static final char SEPARADOR = ';';
  public static final String SEPARADOR_CATEGORIAS = "|";
  public static final char BOM = '﻿';

  public static final String DESCRICAO = "descricao";
  public static final String TIPO = "tipo";
  public static final String DATA = "data";
  public static final String VALOR = "valor";
  public static final String CATEGORIAS = "categorias";
  public static final String STATUS = "status";

  /** Colunas obrigatórias, na ordem do template. */
  public static final List<String> COLUNAS = List.of(DESCRICAO, TIPO, DATA, VALOR, CATEGORIAS);

  /** Data no CSV: {@code dd/mm/aaaa}, estrita (31/02 é recusada). */
  public static final DateTimeFormatter FORMATO_DATA =
      DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT);

  /** Formato do Commons CSV: {@code ;}, aspas duplas quando preciso, quebra de linha CRLF. */
  public static final CSVFormat FORMATO =
      CSVFormat.DEFAULT.builder().setDelimiter(SEPARADOR).setTrim(true).get();

  private LayoutCsv() {}
}
