package br.edu.ifpr.mcp_organizer.mcp.resources;

import java.util.List;
import java.util.Map;

import br.edu.ifpr.mcp_organizer.mcp.utils.McpConsts;
import br.edu.ifpr.mcp_organizer.mcp.utils.McpResourceDefinition;
import br.edu.ifpr.mcp_organizer.services.classification.ClassificationRules;
import io.modelcontextprotocol.server.McpServer.SingleSessionSyncSpecification;
import io.modelcontextprotocol.server.McpServer.SyncSpecification;
import io.modelcontextprotocol.server.McpServerFeatures.SyncResourceSpecification;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema.ReadResourceRequest;
import io.modelcontextprotocol.spec.McpSchema.ReadResourceResult;
import io.modelcontextprotocol.spec.McpSchema.Resource;
import io.modelcontextprotocol.spec.McpSchema.TextResourceContents;

/**
 * Recurso MCP {@code app://regras-classificacao}: expõe as categorias e
 * extensões em uso pelo classificador.
 */
public final class ClassificationRulesResource implements McpResourceDefinition {

   private final ClassificationRules rules;

   public ClassificationRulesResource(ClassificationRules rules) {
      if (rules == null)
         throw new IllegalArgumentException(
            "As regras de classificação não podem ser nulas.");
      this.rules = rules;
   }

   @Override
   public void register(SyncSpecification<SingleSessionSyncSpecification> mcp)
               throws Exception {
      Resource resource = Resource.builder(McpConsts.RESOURCE_RULES_URI,
                                           McpConsts.RESOURCE_RULES_NAME)
                                  .title("Regras de Classificação")
                                  .description("Lista as categorias e " +
                                               "extensões de arquivo em uso.")
                                  .mimeType("text/plain")
                                  .build();

      SyncResourceSpecification spec = new SyncResourceSpecification(resource,
                                                                    this::read);
      mcp.resources(spec);
   }

   @Override
   public ReadResourceResult read(McpSyncServerExchange exchange,
                                  ReadResourceRequest request) {
      StringBuilder sb = new StringBuilder();
      sb.append("=== Regras de Classificação de Arquivos ===\n")
        .append("Categoria padrão (sem extensão ou desconhecida): ")
        .append(rules.getDefaultCategory()).append("\n\n")
        .append("Categorias e extensões:\n");

      for (Map.Entry<String, List<String>> entry :
           rules.getCategoriesToExtensions().entrySet()) {
         String category = entry.getKey();
         List<String> exts = entry.getValue();
         String extText = exts.isEmpty()
                          ? "(qualquer extensão não mapeada ou arquivo sem extensão)"
                          : String.join(", ", exts);
         sb.append("- ").append(category).append(": ").append(extText).append('\n');
      }

      TextResourceContents text =
         TextResourceContents.builder(McpConsts.RESOURCE_RULES_URI,
                                      sb.toString())
                             .mimeType("text/plain")
                             .build();

      return ReadResourceResult.builder(List.of(text)).build();
   }
}
