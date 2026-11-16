package br.edu.ifpr.mcp_organizer.services.organizer.model;

import java.util.List;

/**
 * Lote de execução (journal) gravado antes de mover ({@link BatchStatus#PENDENTE})
 * e atualizado a cada movimento concluído. Permite auditoria e reversão segura
 * ({@code desfazer_organizacao}) mesmo após reinicialização do servidor.
 *
 * @param batchId      identificador único do lote (UUID)
 * @param planId       identificador do plano que originou este lote
 * @param startedAt    timestamp ISO-8601 de início
 * @param finishedAt   timestamp ISO-8601 de término (ou null enquanto pendente)
 * @param status       status atual ({@code PENDENTE}, {@code CONCLUIDO}, {@code PARCIAL}, {@code DESFEITO})
 * @param errorMessage mensagem de erro caso tenha parado como {@code PARCIAL}
 * @param moves        lista de movimentos registrados até o momento
 */
public record OrganizationBatch(
   String batchId,
   String planId,
   String startedAt,
   String finishedAt,
   BatchStatus status,
   String errorMessage,
   List<ExecutedMoveRecord> moves
) {}
