package br.com.poupamais.lancamento.model.entidades;

import br.com.poupamais.lancamento.publico.enums.StatusLancamento;
import br.com.poupamais.lancamento.publico.enums.TipoLancamento;
import br.com.poupamais.shared.exception.RegraNegocioException;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Receita ou despesa de um usuário (RF001). O dono é referenciado só pelo id. */
@Entity
@Table(name = "lancamento")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Lancamento {

  static final String MSG_TRANSICAO_INVALIDA = "Não é possível alterar o status de %s para %s";

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "id_usuario", nullable = false, updatable = false)
  private Long idUsuario;

  @Column(nullable = false, length = 100)
  private String descricao;

  /** Data do lançamento (RN001): dia, mês e ano; as listagens filtram pelo mês dela. */
  @Column(nullable = false)
  private LocalDate data;

  @Column(nullable = false, precision = 16, scale = 2)
  private BigDecimal valor;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private TipoLancamento tipo;

  @Builder.Default
  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private StatusLancamento status = StatusLancamento.ATIVO;

  @Column(name = "data_cadastro", nullable = false, updatable = false)
  private OffsetDateTime dataCadastro;

  @Column(name = "data_atualizacao")
  private OffsetDateTime dataAtualizacao;

  /** Quando foi para a lixeira (DELETADO); volta a {@code null} ao restaurar. */
  @Column(name = "data_exclusao")
  private OffsetDateTime dataExclusao;

  @Builder.Default
  @Setter(lombok.AccessLevel.NONE)
  @OneToMany(mappedBy = "lancamento", cascade = CascadeType.ALL, orphanRemoval = true)
  private List<CategoriaLancamento> categorias = new ArrayList<>();

  @PrePersist
  void antesDeInserir() {
    if (dataCadastro == null) {
      dataCadastro = agora();
    }
  }

  /**
   * Máquina de estados da RN006: valida a transição em {@link StatusLancamento#podeIrPara} e marca
   * a data de atualização. Ir para DELETADO grava {@code dataExclusao}; sair dele (restaurar)
   * limpa.
   *
   * @throws RegraNegocioException (422) se a transição não for permitida
   */
  public void alterarStatus(StatusLancamento novo) {
    if (status == null || !status.podeIrPara(novo)) {
      throw new RegraNegocioException(
          MSG_TRANSICAO_INVALIDA.formatted(nomeStatus(status), nomeStatus(novo)));
    }
    status = novo;
    dataExclusao = novo == StatusLancamento.DELETADO ? agora() : null;
    marcarAtualizacao();
  }

  /** Troca as categorias vinculadas pelas informadas (repetidas contam uma vez). */
  public void definirCategorias(Collection<Long> idsCategorias) {
    Set<Long> novas = new LinkedHashSet<>(idsCategorias);
    categorias.removeIf(c -> !novas.contains(c.getIdCategoria()));
    categorias.forEach(c -> novas.remove(c.getIdCategoria()));
    novas.forEach(idCategoria -> categorias.add(new CategoriaLancamento(this, idCategoria)));
  }

  /** Ids das categorias vinculadas, em ordem crescente. */
  public List<Long> idsCategorias() {
    return categorias.stream().map(CategoriaLancamento::getIdCategoria).sorted().toList();
  }

  public void marcarAtualizacao() {
    dataAtualizacao = agora();
  }

  /** Instante atual em UTC, truncado em segundos (mesmo valor na resposta e depois de reler). */
  private static OffsetDateTime agora() {
    return OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS);
  }

  private static String nomeStatus(StatusLancamento s) {
    return s == null ? "vazio" : s.name();
  }
}
