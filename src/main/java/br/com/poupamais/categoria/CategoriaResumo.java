package br.com.poupamais.categoria;

/** Visão pública de uma categoria, usada pelos outros módulos. */
public record CategoriaResumo(Long id, String descricao, boolean ativa) {}
