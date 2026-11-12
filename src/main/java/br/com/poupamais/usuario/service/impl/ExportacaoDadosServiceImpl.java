package br.com.poupamais.usuario.service.impl;

import br.com.poupamais.categoria.publico.CategoriaFacade;
import br.com.poupamais.categoria.publico.dto.CategoriaResumo;
import br.com.poupamais.lancamento.publico.LancamentoFacade;
import br.com.poupamais.lancamento.publico.dto.LancamentoResumo;
import br.com.poupamais.shared.exception.ErroAutenticacaoException;
import br.com.poupamais.usuario.endpoints.dto.DadosTitularResponse;
import br.com.poupamais.usuario.endpoints.dto.DadosTitularResponse.CategoriaDoLancamento;
import br.com.poupamais.usuario.endpoints.dto.DadosTitularResponse.CategoriaExportada;
import br.com.poupamais.usuario.endpoints.dto.DadosTitularResponse.LancamentoExportado;
import br.com.poupamais.usuario.endpoints.dto.DadosTitularResponse.PerfilExportado;
import br.com.poupamais.usuario.model.entidades.Usuario;
import br.com.poupamais.usuario.model.repositories.FotoPerfilRepository;
import br.com.poupamais.usuario.model.repositories.UsuarioRepository;
import br.com.poupamais.usuario.service.ExportacaoDadosService;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Só leitura e agregação: como no dashboard, devolve direto o DTO do arquivo (sem mapper). Os dados
 * de outros módulos vêm só pelas facades públicas.
 */
@Service
@RequiredArgsConstructor
public class ExportacaoDadosServiceImpl implements ExportacaoDadosService {

  private final UsuarioRepository usuarioRepository;
  private final FotoPerfilRepository fotoRepository;
  private final CategoriaFacade categoriaFacade;
  private final LancamentoFacade lancamentoFacade;
  private Clock relogio = Clock.systemUTC();

  void usarRelogio(Clock relogio) {
    this.relogio = relogio;
  }

  @Override
  @Transactional(readOnly = true)
  public DadosTitularResponse exportar(Long idUsuario) {
    Usuario usuario =
        usuarioRepository
            .findById(idUsuario)
            .orElseThrow(() -> new ErroAutenticacaoException(PerfilServiceImpl.MSG_SESSAO));
    return new DadosTitularResponse(
        relogio.instant().truncatedTo(ChronoUnit.SECONDS),
        perfil(usuario),
        categoriaFacade.listarTodas(idUsuario).stream()
            .map(ExportacaoDadosServiceImpl::categoria)
            .toList(),
        lancamentoFacade.listarTodos(idUsuario).stream()
            .map(ExportacaoDadosServiceImpl::lancamento)
            .toList());
  }

  private PerfilExportado perfil(Usuario usuario) {
    // Lista explícita de campos: senha (hash), senhaAlteradaEm e tokens nunca saem (RNF019)
    return new PerfilExportado(
        usuario.getId(),
        usuario.getNome(),
        usuario.getEmail(),
        usuario.getDataCadastro(),
        usuario.getAceiteTermosEm(),
        fotoRepository.existsById(usuario.getId()));
  }

  private static CategoriaExportada categoria(CategoriaResumo c) {
    return new CategoriaExportada(c.id(), c.descricao(), c.ativa());
  }

  private static LancamentoExportado lancamento(LancamentoResumo l) {
    return new LancamentoExportado(
        l.id(),
        l.descricao(),
        l.tipo(),
        l.mes(),
        l.ano(),
        l.valor(),
        l.status(),
        l.categorias().stream().map(c -> new CategoriaDoLancamento(c.id(), c.descricao())).toList(),
        l.dataCadastro(),
        l.dataAtualizacao());
  }
}
