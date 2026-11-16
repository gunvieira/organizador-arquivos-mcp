package br.edu.ifpr.mcp_organizer.mcp.tools;

import java.io.File;
import java.nio.file.Path;
import java.util.Map;

import br.edu.ifpr.mcp_organizer.mcp.utils.McpConsts;
import br.edu.ifpr.mcp_organizer.mcp.utils.McpToolDefinition;
import br.edu.ifpr.mcp_organizer.mcp.utils.ToolResults;
import br.edu.ifpr.mcp_organizer.utils.json.schema.SchemaLoader;
import io.modelcontextprotocol.server.McpServer.SingleSessionSyncSpecification;
import io.modelcontextprotocol.server.McpServer.SyncSpecification;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.Tool;

/**
 * Ferramenta MCP que informa o caminho do diretório raiz autorizado.
 */
public class AuthPathTool implements McpToolDefinition {

   private static final String JSON_SCHEMA =
                                          "/json-schemas/auth-path.schema.json";

   private final Path root;

   public AuthPathTool(Path root) {
      if (root == null)
         throw new IllegalArgumentException(
            "O diretório raiz autorizado não pode ser nulo.");
      this.root = root;
   }

   @Override
   public void register(SyncSpecification<SingleSessionSyncSpecification> mcp)
               throws Exception {
      Map<String, Object> inputSchema = SchemaLoader.readFromFile(JSON_SCHEMA);

      Tool tool = Tool.builder(McpConsts.APT_TOOL_NAME, inputSchema)
                      .title("Diretório Autorizado")
                      .description("Recupera o diretório (caminho) " +
                                   "autorizado para operações com arquivos " +
                                   "no servidor.")
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
         String pathStr = root.toString();
         if (!pathStr.endsWith(File.separator))
            pathStr = pathStr + File.separator;

         return ToolResults.success(pathStr);
      }
      catch (Exception e) {
         return ToolResults.error(e.getMessage());
      }
   }
}
