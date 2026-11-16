package br.edu.ifpr.mcp_organizer.services.organizer.model;

/**
 * Representa um movimento proposto no plano de organização:
 * {@code sourceRelativePath -> targetRelativePath}.
 *
 * @param sourceRelativePath caminho relativo de origem (a partir da raiz)
 * @param targetRelativePath caminho relativo proposto de destino (a partir da raiz)
 * @param category           categoria atribuída ao arquivo
 */
public record PlannedMove(
   String sourceRelativePath,
   String targetRelativePath,
   String category
) {}
