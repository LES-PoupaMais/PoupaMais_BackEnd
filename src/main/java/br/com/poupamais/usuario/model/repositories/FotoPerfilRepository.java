package br.com.poupamais.usuario.model.repositories;

import br.com.poupamais.usuario.model.entidades.FotoPerfil;
import org.springframework.data.jpa.repository.JpaRepository;

/** Chave = id do usuário dono (RN005). */
public interface FotoPerfilRepository extends JpaRepository<FotoPerfil, Long> {}
