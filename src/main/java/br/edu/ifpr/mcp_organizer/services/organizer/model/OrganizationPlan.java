package br.edu.ifpr.mcp_organizer.services.organizer.model;

import java.util.List;

/**
 * Representa um plano de organização gerado por {@code sugerir_organizacao} e
 * persistido no repositório antes de qualquer movimentação.
 *
 * @param planId          identificador único (UUID) do plano
 * @param createdAt       data/hora ISO-8601 da geração do plano
 * @param targetDirectory caminho relativo da pasta alvo organizada
 * @param recursive       indica se o plano incluiu subdiretórios
 * @param applied         indica se o plano já foi aplicado (um plano só pode ser aplicado uma vez)
 * @param moves           lista de movimentos propostos
 */
public record OrganizationPlan(
   String planId,
   String createdAt,
   String targetDirectory,
   boolean recursive,
   boolean applied,
   List<PlannedMove> moves
) {
   public OrganizationPlan markApplied() {
      return new OrganizationPlan(
         planId, createdAt, targetDirectory, recursive, true, moves);
   }
}
