package br.edu.ifpr.mcp_organizer.mcp.tools;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import br.edu.ifpr.mcp_organizer.mcp.utils.McpConsts;
import br.edu.ifpr.mcp_organizer.mcp.utils.McpToolDefinition;
import br.edu.ifpr.mcp_organizer.mcp.utils.ToolResults;
import br.edu.ifpr.mcp_organizer.services.duplicates.DuplicateFinderService;
import br.edu.ifpr.mcp_organizer.utils.json.schema.SchemaLoader;
import io.modelcontextprotocol.server.McpServer.SingleSessionSyncSpecification;
import io.modelcontextprotocol.server.McpServer.SyncSpecification;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.Tool;

/**
 * Ferramenta MCP {@code encontrar_duplicados}: encontra grupos de arquivos
 * idênticos e informa o espaço desperdiçado.
 */
public class FindDuplicatesTool implements McpToolDefinition {

   private static final Logger LOG =
      LoggerFactory.getLogger(FindDuplicatesTool.class);

   private static final String JSON_SCHEMA =
      "/json-schemas/encontrar-duplicados.schema.json";

   private final DuplicateFinderService duplicateService;

   public FindDuplicatesTool(DuplicateFinderService duplicateService) {
      if (duplicateService == null)
         throw new IllegalArgumentException(
            "O serviço de duplicados não pode ser nulo.");
      this.duplicateService = duplicateService;
   }

   @Override
   public void register(SyncSpecification<SingleSessionSyncSpecification> mcp)
               throws Exception {
      Map<String, Object> inputSchema = SchemaLoader.readFromFile(JSON_SCHEMA);

      Tool tool = Tool.builder(McpConsts.DUPLICATES_TOOL_NAME, inputSchema)
                      .title("Encontrar Arquivos Duplicados")
                      .description("Identifica arquivos duplicados por " +
                                   "tamanho, hash parcial (64 KB) e SHA-256 " +
                                   "completo, informando os grupos e o " +
                                   "espaço desperdiçado.")
                      .build();

      SyncToolSpecification spec = SyncToolSpecification.builder()
                                                        .tool(tool)
                                                        .callHandler(this::run)
                                                        .build();
      mcp.tools(spec);
   }

   @Override
   public CallToolResult run(McpSyncServerExchange exchange,
                             CallToolRequest request) {
      try {
         Map<String, Object> args = request != null && request.arguments() != null
                                    ? request.arguments()
                                    : Map.of();

         String path = args.get(McpConsts.ATTR_PATH) instanceof String s
                       ? s
                       : null;
         boolean recursive = !(args.get(McpConsts.ATTR_RECURSIVE) instanceof Boolean b) || b;
         long minSize = args.get(McpConsts.ATTR_MIN_SIZE) instanceof Number n
                        ? n.longValue()
                        : 1L;

         LOG.info("Executando '{}': path=[{}], recursive=[{}], min_size=[{}]",
                  McpConsts.DUPLICATES_TOOL_NAME, path, recursive, minSize);

         String report = duplicateService.formatDuplicatesReport(
            path, recursive, minSize);
         return ToolResults.success(report);
      }
      catch (Exception ex) {
         LOG.warn("Falha na ferramenta '{}': {}",
                  McpConsts.DUPLICATES_TOOL_NAME, ex.getMessage());
         return ToolResults.error(
            "Não foi possível buscar arquivos duplicados: " + ex.getMessage());
      }
   }
}
