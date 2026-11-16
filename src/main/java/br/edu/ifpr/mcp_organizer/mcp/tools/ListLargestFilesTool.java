package br.edu.ifpr.mcp_organizer.mcp.tools;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import br.edu.ifpr.mcp_organizer.mcp.utils.McpConsts;
import br.edu.ifpr.mcp_organizer.mcp.utils.McpToolDefinition;
import br.edu.ifpr.mcp_organizer.mcp.utils.ToolResults;
import br.edu.ifpr.mcp_organizer.services.search.FileSearchService;
import br.edu.ifpr.mcp_organizer.utils.json.schema.SchemaLoader;
import io.modelcontextprotocol.server.McpServer.SingleSessionSyncSpecification;
import io.modelcontextprotocol.server.McpServer.SyncSpecification;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.Tool;

/**
 * Ferramenta MCP {@code listar_maiores}: lista os maiores arquivos da pasta em
 * ordem decrescente de tamanho.
 */
public class ListLargestFilesTool implements McpToolDefinition {

   private static final Logger LOG =
      LoggerFactory.getLogger(ListLargestFilesTool.class);

   private static final String JSON_SCHEMA =
      "/json-schemas/listar-maiores.schema.json";

   private final FileSearchService searchService;

   public ListLargestFilesTool(FileSearchService searchService) {
      if (searchService == null)
         throw new IllegalArgumentException(
            "O serviço de busca não pode ser nulo.");
      this.searchService = searchService;
   }

   @Override
   public void register(SyncSpecification<SingleSessionSyncSpecification> mcp)
               throws Exception {
      Map<String, Object> inputSchema = SchemaLoader.readFromFile(JSON_SCHEMA);

      Tool tool = Tool.builder(McpConsts.LARGEST_TOOL_NAME, inputSchema)
                      .title("Listar Maiores Arquivos")
                      .description("Lista os maiores arquivos dentro do " +
                                   "diretório autorizado em ordem " +
                                   "decrescente de tamanho.")
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
         int limit = args.get(McpConsts.ATTR_LIMIT) instanceof Number n
                     ? n.intValue()
                     : FileSearchService.DEFAULT_RANK_LIMIT;
         boolean recursive = !(args.get(McpConsts.ATTR_RECURSIVE) instanceof Boolean b) || b;

         LOG.info("Executando '{}': path=[{}], limit=[{}], recursive=[{}]",
                  McpConsts.LARGEST_TOOL_NAME, path, limit, recursive);

         String report = searchService.listLargest(path, limit, recursive);
         return ToolResults.success(report);
      }
      catch (Exception ex) {
         LOG.warn("Falha na ferramenta '{}': {}",
                  McpConsts.LARGEST_TOOL_NAME, ex.getMessage());
         return ToolResults.error(
            "Não foi possível listar os maiores arquivos: " + ex.getMessage());
      }
   }
}
