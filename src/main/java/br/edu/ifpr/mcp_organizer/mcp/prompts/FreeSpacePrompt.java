package br.edu.ifpr.mcp_organizer.mcp.prompts;

import java.util.List;
import java.util.Map;

import br.edu.ifpr.mcp_organizer.mcp.utils.McpConsts;
import br.edu.ifpr.mcp_organizer.mcp.utils.McpPromptDefinition;
import io.modelcontextprotocol.server.McpServer.SingleSessionSyncSpecification;
import io.modelcontextprotocol.server.McpServer.SyncSpecification;
import io.modelcontextprotocol.server.McpServerFeatures.SyncPromptSpecification;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema.GetPromptRequest;
import io.modelcontextprotocol.spec.McpSchema.GetPromptResult;
import io.modelcontextprotocol.spec.McpSchema.Prompt;
import io.modelcontextprotocol.spec.McpSchema.PromptArgument;
import io.modelcontextprotocol.spec.McpSchema.PromptMessage;
import io.modelcontextprotocol.spec.McpSchema.Role;
import io.modelcontextprotocol.spec.McpSchema.TextContent;

/**
 * Prompt MCP {@code liberar_espaco}: orienta o assistente a combinar
 * {@code listar_maiores} e {@code encontrar_duplicados} para sugerir o que
 * revisar, reforçando que a IA nunca deve apagar nenhum arquivo.
 */
public class FreeSpacePrompt implements McpPromptDefinition {

   private static final String DESCRIPTION =
      "Analisa os maiores arquivos e os arquivos duplicados para sugerir " +
      "onde liberar espaço (sem apagar arquivos).";

   @Override
   public void register(SyncSpecification<SingleSessionSyncSpecification> mcp)
               throws Exception {
      Prompt prompt = Prompt.builder(McpConsts.PROMPT_FREE_SPACE)
                            .description(DESCRIPTION)
                            .arguments(List.of(
                               PromptArgument.builder(McpConsts.PROMPT_ATTR_PASTA)
                                             .description("Caminho relativo da pasta a analisar (opcional, padrão é a raiz '.').")
                                             .required(false)
                                             .build()
                            ))
                            .build();

      SyncPromptSpecification spec = new SyncPromptSpecification(prompt,
                                                                 this::run);
      mcp.prompts(spec);
   }

   @Override
   public GetPromptResult run(McpSyncServerExchange exchange,
                              GetPromptRequest request) {
      Map<String, Object> args = request != null && request.arguments() != null
                                 ? request.arguments()
                                 : Map.of();

      String pasta = args.get(McpConsts.PROMPT_ATTR_PASTA) instanceof String s
                     ? s.trim()
                     : "";
      String alvo = pasta.isEmpty() ? "." : pasta;

      String message = """
         Você é um assistente especializado em diagnóstico de armazenamento usando as ferramentas do servidor MCP.

         DADOS INFORMADOS PELO USUÁRIO
         Pasta alvo: %s

         PROCEDIMENTO
         1. Execute a ferramenta '%s' na pasta '%s' para identificar os arquivos que mais ocupam espaço.
         2. Execute a ferramenta '%s' na pasta '%s' para localizar grupos de arquivos idênticos (SHA-256) e calcular o espaço total desperdiçado.
         3. Apresente um relatório claro destacando:
            - Os maiores arquivos encontrados e suas categorias;
            - Os grupos de arquivos duplicados e quanto espaço pode ser recuperado;
            - Recomendações do que o próprio usuário pode revisar manualmente.

         REGRAS OBRIGATÓRIAS
         - NUNCA apague nem tente excluir qualquer arquivo (o servidor não possui ferramenta de exclusão por segurança).
         - Baseie sua análise estritamente nos dados retornados pelas ferramentas.
         """.formatted(
            alvo,
            McpConsts.LARGEST_TOOL_NAME, alvo,
            McpConsts.DUPLICATES_TOOL_NAME, alvo
         );

      TextContent content = TextContent.builder(message).build();
      PromptMessage promptMessage = PromptMessage.builder(Role.USER, content)
                                                 .build();

      return GetPromptResult.builder(List.of(promptMessage))
                            .description(DESCRIPTION)
                            .build();
   }
}
