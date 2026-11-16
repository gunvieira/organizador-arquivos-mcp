package br.edu.ifpr.mcp_organizer.services.organizer.model;

/**
 * Registro no journal de um movimento efetivamente realizado (ou tentado).
 *
 * @param sourceRelativePath         caminho relativo original antes de mover
 * @param plannedTargetRelativePath  caminho relativo que havia sido planejado
 * @param actualTargetRelativePath   caminho relativo efetivo para onde foi movido (após resolução de conflito)
 * @param renamedOnConflict          true se o destino precisou de sufixo {@code nome (N).ext}
 * @param completed                  true se o movimento foi concluído no sistema de arquivos
 */
public record ExecutedMoveRecord(
   String sourceRelativePath,
   String plannedTargetRelativePath,
   String actualTargetRelativePath,
   boolean renamedOnConflict,
   boolean completed
) {}
