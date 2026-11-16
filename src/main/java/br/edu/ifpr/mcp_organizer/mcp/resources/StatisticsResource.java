package br.edu.ifpr.mcp_organizer.mcp.resources;

import java.util.List;

import br.edu.ifpr.mcp_organizer.mcp.utils.McpConsts;
import br.edu.ifpr.mcp_organizer.mcp.utils.McpResourceDefinition;
import br.edu.ifpr.mcp_organizer.services.scan.FileScanner;
import io.modelcontextprotocol.server.McpServer.SingleSessionSyncSpecification;
import io.modelcontextprotocol.server.McpServer.SyncSpecification;
import io.modelcontextprotocol.server.McpServerFeatures.SyncResourceSpecification;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema.ReadResourceRequest;
import io.modelcontextprotocol.spec.McpSchema.ReadResourceResult;
import io.modelcontextprotocol.spec.McpSchema.Resource;
import io.modelcontextprotocol.spec.McpSchema.TextResourceContents;

/**
 * Recurso MCP {@code app://estatisticas}: retorna estatísticas de espaço e
 * contagem de arquivos por categoria em todo o diretório raiz autorizado.
 */
public final class StatisticsResource implements McpResourceDefinition {

   private final FileScanner scanner;

   public StatisticsResource(FileScanner scanner) {
      if (scanner == null)
         throw new IllegalArgumentException(
            "O scanner de arquivos não pode ser nulo.");
      this.scanner = scanner;
   }

   @Override
   public void register(SyncSpecification<SingleSessionSyncSpecification> mcp)
               throws Exception {
      Resource resource = Resource.builder(McpConsts.RESOURCE_STATS_URI,
                                           McpConsts.RESOURCE_STATS_NAME)
                                  .title("Estatísticas por Categoria")
                                  .description("Retorna o espaço ocupado e o " +
                                               "total de arquivos por " +
                                               "categoria na raiz autorizada.")
                                  .mimeType("text/plain")
                                  .build();

      SyncResourceSpecification spec = new SyncResourceSpecification(resource,
                                                                    this::read);
      mcp.resources(spec);
   }

   @Override
   public ReadResourceResult read(McpSyncServerExchange exchange,
                                  ReadResourceRequest request) {
      String content = scanner.formatScanReport(null, true);

      TextResourceContents text =
         TextResourceContents.builder(McpConsts.RESOURCE_STATS_URI, content)
                             .mimeType("text/plain")
                             .build();

      return ReadResourceResult.builder(List.of(text)).build();
   }
}
