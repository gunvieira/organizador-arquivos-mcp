package br.edu.ifpr.mcp_organizer.mcp.tools;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import br.edu.ifpr.mcp_organizer.mcp.utils.McpConsts;
import br.edu.ifpr.mcp_organizer.mcp.utils.McpToolDefinition;
import br.edu.ifpr.mcp_organizer.mcp.utils.ToolResults;
import br.edu.ifpr.mcp_organizer.services.organizer.OrganizationExecutor;
import br.edu.ifpr.mcp_organizer.services.organizer.OrganizationExecutor.ExecutionOutcome;
import br.edu.ifpr.mcp_organizer.utils.json.schema.SchemaLoader;
import io.modelcontextprotocol.server.McpServer.SingleSessionSyncSpecification;
import io.modelcontextprotocol.server.McpServer.SyncSpecification;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.Tool;

/**
 * Ferramenta MCP {@code aplicar_organizacao}: executa um plano previamente
 * gerado a partir do seu {@code plan_id}.
 */
public class ApplyOrganizationTool implements McpToolDefinition {

   private static final Logger LOG =
      LoggerFactory.getLogger(ApplyOrganizationTool.class);

   private static final String JSON_SCHEMA =
      "/json-schemas/aplicar-organizacao.schema.json";

   private final OrganizationExecutor executor;

   public ApplyOrganizationTool(OrganizationExecutor executor) {
      if (executor == null)
         throw new IllegalArgumentException(
            "O executor de organização não pode ser nulo.");
      this.executor = executor;
   }

   @Override
   public void register(SyncSpecification<SingleSessionSyncSpecification> mcp)
               throws Exception {
      Map<String, Object> inputSchema = SchemaLoader.readFromFile(JSON_SCHEMA);

      Tool tool = Tool.builder(McpConsts.APPLY_ORG_TOOL_NAME, inputSchema)
                      .title("Aplicar Plano de Organização")
                      .description("Aplica um plano de organização gerado " +
                                   "por 'sugerir_organizacao' informando " +
                                   "apenas o plan_id.")
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

         String planId = args.get(McpConsts.ATTR_PLAN_ID) instanceof String s
                         ? s
                         : null;

         LOG.info("Executando '{}': plan_id=[{}]",
                  McpConsts.APPLY_ORG_TOOL_NAME, planId);

         ExecutionOutcome outcome = executor.executePlan(planId);
         if (outcome.partialFailure())
            return ToolResults.error(outcome.report());

         return ToolResults.success(outcome.report());
      }
      catch (Exception ex) {
         LOG.warn("Falha na ferramenta '{}': {}",
                  McpConsts.APPLY_ORG_TOOL_NAME, ex.getMessage());
         return ToolResults.error(
            "Não foi possível aplicar o plano de organização: " +
            ex.getMessage());
      }
   }
}
