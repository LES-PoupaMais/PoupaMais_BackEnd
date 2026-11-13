package br.com.poupamais.arquivo.service.importacao;

import java.util.List;
import org.apache.commons.csv.CSVFormat;

/** Layout do CSV de lançamentos (RN012), compartilhado pela importação e pela exportação. */
public final class LayoutCsv {

  public static final char SEPARADOR = ';';
  public static final String SEPARADOR_CATEGORIAS = "|";
  public static final char BOM = '﻿';

  public static final String DESCRICAO = "descricao";
  public static final String TIPO = "tipo";
  public static final String MES = "mes";
  public static final String ANO = "ano";
  public static final String VALOR = "valor";
  public static final String CATEGORIAS = "categorias";
  public static final String STATUS = "status";

  /** Colunas obrigatórias, na ordem do template. */
  public static final List<String> COLUNAS = List.of(DESCRICAO, TIPO, MES, ANO, VALOR, CATEGORIAS);

  /** Formato do Commons CSV: {@code ;}, aspas duplas quando preciso, quebra de linha CRLF. */
  public static final CSVFormat FORMATO =
      CSVFormat.DEFAULT.builder().setDelimiter(SEPARADOR).setTrim(true).get();

  private LayoutCsv() {}
}
