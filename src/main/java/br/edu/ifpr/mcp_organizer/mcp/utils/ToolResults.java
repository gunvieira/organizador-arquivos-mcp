package br.edu.ifpr.mcp_organizer.mcp.utils;

import java.util.List;

import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;

/**
 * Utilitário centralizado para construir resultados de ferramentas MCP
 * ({@link CallToolResult}), evitando duplicação de código (princípio DRY).
 */
public final class ToolResults {

   private static final String DEFAULT_ERROR_MSG =
      "Erro desconhecido ao executar a ferramenta.";

   private ToolResults() {}

   /**
    * Cria um resultado de sucesso ({@code isError = false}).
    *
    * @param message texto de retorno da ferramenta
    * @return resultado padronizado
    */
   public static CallToolResult success(String message) {
      return build(message, false);
   }

   /**
    * Cria um resultado de erro ({@code isError = true}).
    *
    * @param message mensagem de erro legível em português
    * @return resultado padronizado indicando falha
    */
   public static CallToolResult error(String message) {
      return build(message, true);
   }

   private static CallToolResult build(String message, boolean isError) {
      String msg = (message == null || message.trim().isEmpty())
                   ? DEFAULT_ERROR_MSG
                   : message;
      TextContent textContent = TextContent.builder(msg).build();

      return CallToolResult.builder()
                           .content(List.of(textContent))
                           .isError(isError)
                           .build();
   }
}
