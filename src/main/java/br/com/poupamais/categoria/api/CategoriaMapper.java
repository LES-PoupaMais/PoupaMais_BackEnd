package br.com.poupamais.categoria.api;

import br.com.poupamais.categoria.api.dto.CategoriaResponse;
import br.com.poupamais.categoria.model.Categoria;
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
}
