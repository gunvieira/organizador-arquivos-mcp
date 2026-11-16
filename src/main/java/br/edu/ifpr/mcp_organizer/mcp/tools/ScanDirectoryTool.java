package br.edu.ifpr.mcp_organizer.mcp.tools;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import br.edu.ifpr.mcp_organizer.mcp.utils.McpConsts;
import br.edu.ifpr.mcp_organizer.mcp.utils.McpToolDefinition;
import br.edu.ifpr.mcp_organizer.mcp.utils.ToolResults;
import br.edu.ifpr.mcp_organizer.services.scan.FileScanner;
import br.edu.ifpr.mcp_organizer.utils.json.schema.SchemaLoader;
import io.modelcontextprotocol.server.McpServer.SingleSessionSyncSpecification;
import io.modelcontextprotocol.server.McpServer.SyncSpecification;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.Tool;

/**
 * Ferramenta MCP {@code escanear_pasta}: analisa uma pasta e retorna o total
 * de arquivos, espaço por categoria e uma amostra.
 */
public class ScanDirectoryTool implements McpToolDefinition {

   private static final Logger LOG =
      LoggerFactory.getLogger(ScanDirectoryTool.class);

   private static final String JSON_SCHEMA =
      "/json-schemas/escanear-pasta.schema.json";

   private final FileScanner scanner;

   public ScanDirectoryTool(FileScanner scanner) {
      if (scanner == null)
         throw new IllegalArgumentException(
            "O scanner de arquivos não pode ser nulo.");
      this.scanner = scanner;
   }

   @Override
   public void register(SyncSpecification<SingleSessionSyncSpecification> mcp)
               throws Exception {
      Map<String, Object> inputSchema = SchemaLoader.readFromFile(JSON_SCHEMA);

      Tool tool = Tool.builder(McpConsts.SCAN_TOOL_NAME, inputSchema)
                      .title("Escanear Pasta")
                      .description("Escaneia uma pasta dentro do diretório " +
                                   "autorizado, retornando o total de " +
                                   "arquivos, espaço ocupado por categoria " +
                                   "e uma amostra dos arquivos.")
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

         LOG.info("Executando '{}': path=[{}], recursive=[{}]",
                  McpConsts.SCAN_TOOL_NAME, path, recursive);

         String report = scanner.formatScanReport(path, recursive);
         return ToolResults.success(report);
      }
      catch (Exception ex) {
         LOG.warn("Falha na ferramenta '{}': {}",
                  McpConsts.SCAN_TOOL_NAME, ex.getMessage());
         return ToolResults.error(
            "Não foi possível escanear a pasta: " + ex.getMessage());
      }
   }
}
