package br.com.poupamais.lancamento.endpoints.dto;

import java.util.List;

/**
 * Uma página da listagem: os itens e os números para o front montar "Mostrar mais" e "Mostrando X
 * de Y". {@code pagina} começa em 0; página além do fim vem com {@code itens} vazio e o {@code
 * total} correto.
 */
public record PaginaResponse<T>(
    List<T> itens, int pagina, int tamanho, long total, int totalPaginas) {}
