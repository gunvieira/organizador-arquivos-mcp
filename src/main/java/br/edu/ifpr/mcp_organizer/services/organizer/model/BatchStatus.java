package br.edu.ifpr.mcp_organizer.services.organizer.model;

/**
 * Status do ciclo de vida de um lote de organização (journal).
 */
public enum BatchStatus {
   /** Journal gravado antes de iniciar as movimentações. */
   PENDENTE,
   /** Todos os movimentos do lote foram concluídos com sucesso. */
   CONCLUIDO,
   /** Um movimento falhou no meio; execução interrompida, mantendo registrado o que já foi movido. */
   PARCIAL,
   /** Lote já revertido por {@code desfazer_organizacao}. */
   DESFEITO
}
