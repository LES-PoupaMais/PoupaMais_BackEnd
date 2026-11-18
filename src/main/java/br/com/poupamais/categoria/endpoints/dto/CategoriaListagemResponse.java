package br.com.poupamais.categoria.endpoints.dto;

/**
 * Item de {@code GET /api/categorias}: a categoria e quantos lançamentos ATIVO/INATIVO dela caem no
 * mês consultado.
 */
public record CategoriaListagemResponse(
    Long id, String descricao, boolean ativa, long quantidadeLancamentos) {}
