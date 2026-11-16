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
 * Prompt MCP {@code organizar_pasta}: orienta o assistente de IA a seguir o
 * fluxo seguro {@code escanear_pasta -> sugerir_organizacao -> mostrar o plano
 * e pedir confirmação explícita -> aplicar_organizacao}.
 */
public class OrganizeFolderPrompt implements McpPromptDefinition {

   private static final String DESCRIPTION =
      "Orienta a organização segura de uma pasta em categorias, exigindo " +
      "confirmação do usuário antes de aplicar o plano.";

   @Override
   public void register(SyncSpecification<SingleSessionSyncSpecification> mcp)
               throws Exception {
      Prompt prompt = Prompt.builder(McpConsts.PROMPT_ORGANIZE)
                            .description(DESCRIPTION)
                            .arguments(List.of(
                               PromptArgument.builder(McpConsts.PROMPT_ATTR_PASTA)
                                             .description("Caminho relativo da pasta a organizar (opcional, padrão é a raiz '.').")
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
         Você é um assistente responsável por organizar arquivos usando exclusivamente as ferramentas do servidor MCP.

         DADOS INFORMADOS PELO USUÁRIO
         Pasta alvo: %s

         PROCEDIMENTO OBRIGATÓRIO (SIGA NESTA ORDEM)
         1. Chame a ferramenta '%s' para inspecionar a pasta '%s' e entender a distribuição atual de arquivos por categoria.
         2. Chame a ferramenta '%s' na pasta '%s' para gerar um plano de organização (que retornará um 'plan_id' e a lista de movimentos propostos).
         3. Apresente ao usuário o resumo do plano gerado (categorias e movimentos 'origem -> destino') e PEÇA CONFIRMAÇÃO EXPLÍCITA antes de mover qualquer arquivo.
         4. SOMENTE após o usuário confirmar, chame a ferramenta '%s' informando o 'plan_id' gerado.
         5. Caso o usuário deseje reverter após a aplicação, utilize a ferramenta '%s'.

         REGRAS DE SEGURANÇA
         - Nunca execute '%s' sem antes mostrar o plano e obter confirmação clara do usuário.
         - Nunca invente caminhos ou 'plan_id'.
         - Informe ao usuário que conflitos de nome nunca sobrescrevem arquivos (são renomeados com sufixo 'nome (1).ext').
         """.formatted(
            alvo,
            McpConsts.SCAN_TOOL_NAME, alvo,
            McpConsts.SUGGEST_ORG_TOOL_NAME, alvo,
            McpConsts.APPLY_ORG_TOOL_NAME,
            McpConsts.UNDO_ORG_TOOL_NAME,
            McpConsts.APPLY_ORG_TOOL_NAME
         );

      TextContent content = TextContent.builder(message).build();
      PromptMessage promptMessage = PromptMessage.builder(Role.USER, content)
                                                 .build();

      return GetPromptResult.builder(List.of(promptMessage))
                            .description(DESCRIPTION)
                            .build();
   }
}
