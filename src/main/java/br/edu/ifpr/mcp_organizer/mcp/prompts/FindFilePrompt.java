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
 * Prompt MCP {@code encontrar_arquivo}: recebe o argumento obrigatório
 * {@code descricao} e instrui a IA a traduzi-lo em filtros da ferramenta
 * {@code buscar_arquivos}.
 */
public class FindFilePrompt implements McpPromptDefinition {

   private static final String DESCRIPTION =
      "Traduz uma descrição em linguagem natural para filtros da ferramenta " +
      "buscar_arquivos.";

   @Override
   public void register(SyncSpecification<SingleSessionSyncSpecification> mcp)
               throws Exception {
      Prompt prompt = Prompt.builder(McpConsts.PROMPT_FIND_FILE)
                            .description(DESCRIPTION)
                            .arguments(List.of(
                               PromptArgument.builder(McpConsts.PROMPT_ATTR_DESCRICAO)
                                             .description("Descrição do arquivo procurado (ex.: 'planilha grande do ano passado' ou 'PDF de contrato').")
                                             .required(true)
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
      Map<String, Object> args = request != null ? request.arguments() : null;

      if (args == null || args.isEmpty() ||
          !(args.get(McpConsts.PROMPT_ATTR_DESCRICAO) instanceof String descricao) ||
          descricao.trim().isEmpty()) {
         throw new IllegalArgumentException(
            "O argumento obrigatório '" + McpConsts.PROMPT_ATTR_DESCRICAO +
            "' não foi informado.");
      }

      String descTrimmed = descricao.trim();

      String message = """
         Você é um assistente especializado em localizar arquivos usando a ferramenta '%s' do servidor MCP.

         DESCRIÇÃO INFORMADA PELO USUÁRIO
         "%s"

         PROCEDIMENTO
         1. Analise a descrição acima e traduza-a nos filtros apropriados da ferramenta '%s':
            - 'name': padrão glob do nome (ex.: '*contrato*', '*.pdf');
            - 'category': uma das categorias válidas (Imagens, Design, Documentos, Planilhas, Apresentacoes, Videos, Audios, Compactados, Outros);
            - 'extensions': lista de extensões específicas (ex.: ["pdf", "docx"]);
            - 'min_size' / 'max_size': limites de tamanho em bytes;
            - 'modified_after' / 'modified_before': datas no formato ISO YYYY-MM-DD;
            - 'recursive': true para procurar em todas as subpastas;
            - 'max_results': limite entre 1 e 100 (padrão 20).
         2. Execute '%s' com os filtros escolhidos.
         3. Caso nenhum arquivo seja retornado, relaxe o filtro mais restritivo e tente uma segunda busca, explicando os resultados ao usuário.
         """.formatted(
            McpConsts.SEARCH_TOOL_NAME,
            descTrimmed,
            McpConsts.SEARCH_TOOL_NAME,
            McpConsts.SEARCH_TOOL_NAME
         );

      TextContent content = TextContent.builder(message).build();
      PromptMessage promptMessage = PromptMessage.builder(Role.USER, content)
                                                 .build();

      return GetPromptResult.builder(List.of(promptMessage))
                            .description(DESCRIPTION)
                            .build();
   }
}
