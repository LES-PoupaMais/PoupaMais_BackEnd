package br.com.poupamais.categoria.endpoints.mappers;

import br.com.poupamais.categoria.endpoints.dto.CategoriaListagemResponse;
import br.com.poupamais.categoria.endpoints.dto.CategoriaResponse;
import br.com.poupamais.categoria.model.entidades.Categoria;
import br.com.poupamais.categoria.service.CategoriaComQuantidade;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class CategoriaMapper {

  public CategoriaResponse paraResponse(Categoria categoria) {
    return new CategoriaResponse(categoria.getId(), categoria.getDescricao(), categoria.isAtiva());
  }

  public List<CategoriaResponse> paraResponse(List<Categoria> categorias) {
    return categorias.stream().map(this::paraResponse).toList();
  }

  public List<CategoriaListagemResponse> paraListagem(List<CategoriaComQuantidade> itens) {
    return itens.stream()
        .map(i ->
            new CategoriaListagemResponse(
                i.categoria().getId(),
                i.categoria().getDescricao(),
                i.categoria().isAtiva(),
                i.quantidadeLancamentos()))
        .toList();
  }
}
