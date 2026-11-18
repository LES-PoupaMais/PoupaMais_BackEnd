package br.com.poupamais.categoria.service;

import br.com.poupamais.categoria.model.entidades.Categoria;

/** Categoria da listagem com a quantidade de lançamentos ATIVO/INATIVO no mês consultado. */
public record CategoriaComQuantidade(Categoria categoria, long quantidadeLancamentos) {}
