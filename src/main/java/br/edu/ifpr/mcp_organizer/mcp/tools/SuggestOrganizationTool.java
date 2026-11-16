package br.edu.ifpr.mcp_organizer.mcp.tools;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import br.edu.ifpr.mcp_organizer.mcp.utils.McpConsts;
import br.edu.ifpr.mcp_organizer.mcp.utils.McpToolDefinition;
import br.edu.ifpr.mcp_organizer.mcp.utils.ToolResults;
import br.edu.ifpr.mcp_organizer.services.organizer.OrganizationPlanner;
import br.edu.ifpr.mcp_organizer.utils.json.schema.SchemaLoader;
import io.modelcontextprotocol.server.McpServer.SingleSessionSyncSpecification;
import io.modelcontextprotocol.server.McpServer.SyncSpecification;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.Tool;

/**
 * Ferramenta MCP {@code sugerir_organizacao}: gera um plano de movimentação
 * por categoria ({@code plan_id}) sem alterar nenhum arquivo.
 */
public class SuggestOrganizationTool implements McpToolDefinition {

   private static final Logger LOG =
      LoggerFactory.getLogger(SuggestOrganizationTool.class);

   private static final String JSON_SCHEMA =
      "/json-schemas/sugerir-organizacao.schema.json";

   private final OrganizationPlanner planner;

   public SuggestOrganizationTool(OrganizationPlanner planner) {
      if (planner == null)
         throw new IllegalArgumentException(
            "O planejador de organização não pode ser nulo.");
      this.planner = planner;
   }

   @Override
   public void register(SyncSpecification<SingleSessionSyncSpecification> mcp)
               throws Exception {
      Map<String, Object> inputSchema = SchemaLoader.readFromFile(JSON_SCHEMA);

      Tool tool = Tool.builder(McpConsts.SUGGEST_ORG_TOOL_NAME, inputSchema)
                      .title("Sugerir Organização de Arquivos")
                      .description("Gera um plano de organização (plan_id e " +
                                   "lista de movimentos origem -> destino " +
                                   "por categoria) sem mover nenhum arquivo.")
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
         // Padrão false para sugerir_organizacao conforme especificação 3.4
         boolean recursive = Boolean.TRUE.equals(
            args.get(McpConsts.ATTR_RECURSIVE));

         LOG.info("Executando '{}': path=[{}], recursive=[{}]",
                  McpConsts.SUGGEST_ORG_TOOL_NAME, path, recursive);

         String report = planner.suggestOrganizationReport(path, recursive);
         return ToolResults.success(report);
      }
      catch (Exception ex) {
         LOG.warn("Falha na ferramenta '{}': {}",
                  McpConsts.SUGGEST_ORG_TOOL_NAME, ex.getMessage());
         return ToolResults.error(
            "Não foi possível gerar o plano de organização: " +
            ex.getMessage());
      }
   }
}
