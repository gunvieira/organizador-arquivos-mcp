package br.edu.ifpr.mcp_organizer.mcp.tools;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import br.edu.ifpr.mcp_organizer.mcp.utils.McpConsts;
import br.edu.ifpr.mcp_organizer.mcp.utils.McpToolDefinition;
import br.edu.ifpr.mcp_organizer.mcp.utils.ToolResults;
import br.edu.ifpr.mcp_organizer.services.organizer.UndoService;
import br.edu.ifpr.mcp_organizer.utils.json.schema.SchemaLoader;
import io.modelcontextprotocol.server.McpServer.SingleSessionSyncSpecification;
import io.modelcontextprotocol.server.McpServer.SyncSpecification;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.Tool;

/**
 * Ferramenta MCP {@code desfazer_organizacao}: reverte o último lote de
 * organização aplicado (ou um {@code batch_id} específico) em ordem inversa.
 */
public class UndoOrganizationTool implements McpToolDefinition {

   private static final Logger LOG =
      LoggerFactory.getLogger(UndoOrganizationTool.class);

   private static final String JSON_SCHEMA =
      "/json-schemas/desfazer-organizacao.schema.json";

   private final UndoService undoService;

   public UndoOrganizationTool(UndoService undoService) {
      if (undoService == null)
         throw new IllegalArgumentException(
            "O serviço de reversão não pode ser nulo.");
      this.undoService = undoService;
   }

   @Override
   public void register(SyncSpecification<SingleSessionSyncSpecification> mcp)
               throws Exception {
      Map<String, Object> inputSchema = SchemaLoader.readFromFile(JSON_SCHEMA);

      Tool tool = Tool.builder(McpConsts.UNDO_ORG_TOOL_NAME, inputSchema)
                      .title("Desfazer Organização")
                      .description("Reverte o último lote de organização " +
                                   "aplicado e ainda não desfeito em ordem " +
                                   "inversa, sem sobrescrever arquivos.")
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

         String batchId = args.get(McpConsts.ATTR_BATCH_ID) instanceof String s
                          ? s
                          : null;

         LOG.info("Executando '{}': batch_id=[{}]",
                  McpConsts.UNDO_ORG_TOOL_NAME, batchId);

         String report = undoService.undo(batchId);
         return ToolResults.success(report);
      }
      catch (Exception ex) {
         LOG.warn("Falha na ferramenta '{}': {}",
                  McpConsts.UNDO_ORG_TOOL_NAME, ex.getMessage());
         return ToolResults.error(
            "Não foi possível desfazer a organização: " + ex.getMessage());
      }
   }
}
