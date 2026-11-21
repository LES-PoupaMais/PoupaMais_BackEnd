package br.com.poupamais.lancamento.endpoints.dto;

import br.com.poupamais.lancamento.publico.enums.StatusLancamento;
import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Status opcional no corpo: vazio, ATIVO ou INATIVO. DELETADO → 400 (a lixeira é pelo {@code
 * DELETE}).
 */
@Target({ElementType.FIELD, ElementType.RECORD_COMPONENT, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = StatusAtivoOuInativo.Validador.class)
public @interface StatusAtivoOuInativo {

  String message() default "Informe o status ATIVO ou INATIVO";

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};

  class Validador implements ConstraintValidator<StatusAtivoOuInativo, StatusLancamento> {
    @Override
    public boolean isValid(StatusLancamento status, ConstraintValidatorContext contexto) {
      return status != StatusLancamento.DELETADO;
    }
  }
}
