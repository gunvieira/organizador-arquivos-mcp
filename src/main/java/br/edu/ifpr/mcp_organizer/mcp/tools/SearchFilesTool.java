package br.edu.ifpr.mcp_organizer.mcp.tools;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import br.edu.ifpr.mcp_organizer.mcp.utils.McpConsts;
import br.edu.ifpr.mcp_organizer.mcp.utils.McpToolDefinition;
import br.edu.ifpr.mcp_organizer.mcp.utils.ToolResults;
import br.edu.ifpr.mcp_organizer.services.search.FileCriteria;
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
 * Ferramenta MCP {@code buscar_arquivos}: pesquisa arquivos combinando filtros
 * de padrão glob, categoria, extensões, tamanho e datas de modificação.
 */
public class SearchFilesTool implements McpToolDefinition {

   private static final Logger LOG =
      LoggerFactory.getLogger(SearchFilesTool.class);

   private static final String JSON_SCHEMA =
      "/json-schemas/buscar-arquivos.schema.json";

   private final FileSearchService searchService;

   public SearchFilesTool(FileSearchService searchService) {
      if (searchService == null)
         throw new IllegalArgumentException(
            "O serviço de busca não pode ser nulo.");
      this.searchService = searchService;
   }

   @Override
   public void register(SyncSpecification<SingleSessionSyncSpecification> mcp)
               throws Exception {
      Map<String, Object> inputSchema = SchemaLoader.readFromFile(JSON_SCHEMA);

      Tool tool = Tool.builder(McpConsts.SEARCH_TOOL_NAME, inputSchema)
                      .title("Buscar Arquivos")
                      .description("Busca arquivos dentro do diretório " +
                                   "autorizado combinando filtros de nome " +
                                   "(glob), categoria, extensões, tamanho " +
                                   "e data de modificação.")
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

         String name = getString(args, McpConsts.SEARCH_ATTR_NAME);
         String category = getString(args, McpConsts.SEARCH_ATTR_CATEGORY);
         List<String> extensions = getStringList(args, McpConsts.SEARCH_ATTR_EXTENSIONS);
         Long minSize = getLong(args, McpConsts.ATTR_MIN_SIZE);
         Long maxSize = getLong(args, McpConsts.ATTR_MAX_SIZE);
         LocalDate modifiedAfter = FileSearchService.parseIsoDate(
            getString(args, McpConsts.SEARCH_ATTR_MODIFIED_AFTER),
            McpConsts.SEARCH_ATTR_MODIFIED_AFTER);
         LocalDate modifiedBefore = FileSearchService.parseIsoDate(
            getString(args, McpConsts.SEARCH_ATTR_MODIFIED_BEFORE),
            McpConsts.SEARCH_ATTR_MODIFIED_BEFORE);

         boolean recursive = !(args.get(McpConsts.ATTR_RECURSIVE) instanceof Boolean b) || b;
         int maxResults = args.get(McpConsts.SEARCH_ATTR_MAX_RESULTS) instanceof Number n
                          ? n.intValue()
                          : FileCriteria.DEFAULT_MAX_RESULTS;

         FileCriteria criteria = FileCriteria.builder()
            .nameGlob(name)
            .category(category)
            .extensions(extensions)
            .minSize(minSize)
            .maxSize(maxSize)
            .modifiedAfter(modifiedAfter)
            .modifiedBefore(modifiedBefore)
            .recursive(recursive)
            .maxResults(maxResults)
            .build();

         LOG.info("Executando '{}': name=[{}], category=[{}], maxResults=[{}]",
                  McpConsts.SEARCH_TOOL_NAME, name, category, maxResults);

         String report = searchService.search(criteria);
         return ToolResults.success(report);
      }
      catch (Exception ex) {
         LOG.warn("Falha na ferramenta '{}': {}",
                  McpConsts.SEARCH_TOOL_NAME, ex.getMessage());
         return ToolResults.error(
            "Não foi possível concluir a busca: " + ex.getMessage());
      }
   }

   private static String getString(Map<String, Object> args, String key) {
      Object v = args.get(key);
      return v instanceof String s ? s : null;
   }

   private static Long getLong(Map<String, Object> args, String key) {
      Object v = args.get(key);
      if (v instanceof Number n)
         return n.longValue();
      if (v instanceof String s && !s.isBlank()) {
         try {
            return Long.parseLong(s.trim());
         }
         catch (NumberFormatException ex) {
            throw new IllegalArgumentException(
               "Valor numérico inválido para '" + key + "': " + s);
         }
      }
      return null;
   }

   private static List<String> getStringList(Map<String, Object> args,
                                             String key) {
      Object v = args.get(key);
      if (v instanceof List<?> list)
         return list.stream().map(String::valueOf).toList();
      if (v instanceof String s && !s.isBlank())
         return List.of(s.split(","));
      return List.of();
   }
}
