package br.edu.ifpr.mcp_organizer.mcp.resources;

import java.util.List;

import br.edu.ifpr.mcp_organizer.mcp.utils.McpConsts;
import br.edu.ifpr.mcp_organizer.mcp.utils.McpResourceDefinition;
import br.edu.ifpr.mcp_organizer.services.organizer.OrganizationRepository;
import br.edu.ifpr.mcp_organizer.services.organizer.model.ExecutedMoveRecord;
import br.edu.ifpr.mcp_organizer.services.organizer.model.OrganizationBatch;
import io.modelcontextprotocol.server.McpServer.SingleSessionSyncSpecification;
import io.modelcontextprotocol.server.McpServer.SyncSpecification;
import io.modelcontextprotocol.server.McpServerFeatures.SyncResourceSpecification;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema.ReadResourceRequest;
import io.modelcontextprotocol.spec.McpSchema.ReadResourceResult;
import io.modelcontextprotocol.spec.McpSchema.Resource;
import io.modelcontextprotocol.spec.McpSchema.TextResourceContents;

/**
 * Recurso MCP {@code app://historico}: retorna o histórico de lotes de
 * organização aplicados e desfeitos.
 */
public final class HistoryResource implements McpResourceDefinition {

   private final OrganizationRepository repository;

   public HistoryResource(OrganizationRepository repository) {
      if (repository == null)
         throw new IllegalArgumentException(
            "O repositório de organização não pode ser nulo.");
      this.repository = repository;
   }

   @Override
   public void register(SyncSpecification<SingleSessionSyncSpecification> mcp)
               throws Exception {
      Resource resource = Resource.builder(McpConsts.RESOURCE_HISTORY_URI,
                                           McpConsts.RESOURCE_HISTORY_NAME)
                                  .title("Histórico de Organizações")
                                  .description("Retorna o histórico de lotes " +
                                               "de organização aplicados e " +
                                               "desfeitos.")
                                  .mimeType("text/plain")
                                  .build();

      SyncResourceSpecification spec = new SyncResourceSpecification(resource,
                                                                    this::read);
      mcp.resources(spec);
   }

   @Override
   public ReadResourceResult read(McpSyncServerExchange exchange,
                                  ReadResourceRequest request) {
      List<OrganizationBatch> batches = repository.findAllBatches();
      StringBuilder sb = new StringBuilder();
      sb.append("=== Histórico de Organizações ===\n")
        .append("Total de lotes registrados: ").append(batches.size()).append('\n');

      if (batches.isEmpty()) {
         sb.append("\nNenhuma organização foi aplicada até o momento.\n");
      }
      else {
         for (int i = 0; i < batches.size(); i++) {
            OrganizationBatch b = batches.get(i);
            long completedMoves = b.moves() == null ? 0 :
               b.moves().stream().filter(ExecutedMoveRecord::completed).count();

            sb.append("\nLote ").append(i + 1).append(":\n")
              .append("  batch_id: ").append(b.batchId()).append('\n')
              .append("  plan_id: ").append(b.planId()).append('\n')
              .append("  status: ").append(b.status()).append('\n')
              .append("  iniciado em: ").append(b.startedAt()).append('\n')
              .append("  finalizado em: ")
              .append(b.finishedAt() != null ? b.finishedAt() : "-").append('\n')
              .append("  movimentos concluídos: ").append(completedMoves).append('\n');

            if (b.errorMessage() != null && !b.errorMessage().isBlank())
               sb.append("  erro: ").append(b.errorMessage()).append('\n');
         }
      }

      TextResourceContents text =
         TextResourceContents.builder(McpConsts.RESOURCE_HISTORY_URI,
                                      sb.toString())
                             .mimeType("text/plain")
                             .build();

      return ReadResourceResult.builder(List.of(text)).build();
   }
}
