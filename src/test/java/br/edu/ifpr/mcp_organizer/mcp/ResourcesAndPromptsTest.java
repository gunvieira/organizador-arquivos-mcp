package br.edu.ifpr.mcp_organizer.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import br.edu.ifpr.mcp_organizer.main.ServerComposition;
import br.edu.ifpr.mcp_organizer.mcp.prompts.FindFilePrompt;
import br.edu.ifpr.mcp_organizer.mcp.prompts.FreeSpacePrompt;
import br.edu.ifpr.mcp_organizer.mcp.prompts.OrganizeFolderPrompt;
import br.edu.ifpr.mcp_organizer.mcp.resources.ClassificationRulesResource;
import br.edu.ifpr.mcp_organizer.mcp.resources.HistoryResource;
import br.edu.ifpr.mcp_organizer.mcp.resources.StatisticsResource;
import br.edu.ifpr.mcp_organizer.mcp.utils.McpConsts;
import br.edu.ifpr.mcp_organizer.services.organizer.model.OrganizationPlan;
import io.modelcontextprotocol.spec.McpSchema.GetPromptRequest;
import io.modelcontextprotocol.spec.McpSchema.GetPromptResult;
import io.modelcontextprotocol.spec.McpSchema.ReadResourceRequest;
import io.modelcontextprotocol.spec.McpSchema.ReadResourceResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.TextResourceContents;

class ResourcesAndPromptsTest {

   @TempDir
   Path tempDir;

   private ServerComposition composition;

   @BeforeEach
   void setUp() throws IOException {
      composition = new ServerComposition(tempDir);
      Files.writeString(tempDir.resolve("foto.png"), "12345");
   }

   @Test
   @DisplayName("ServerComposition deve registrar todas as 9 tools, 5 resources e 3 prompts")
   void shouldRegisterAllToolsResourcesAndPrompts() {
      assertEquals(9, composition.getTools().size());
      assertEquals(5, composition.getResources().size());
      assertEquals(3, composition.getPrompts().size());
   }

   @Test
   @DisplayName("StatisticsResource (app://estatisticas) deve retornar espaço por categoria")
   void statisticsResourceShouldReturnCategoryBreakdown() {
      StatisticsResource res = new StatisticsResource(composition.getFileScanner());
      ReadResourceResult result = res.read(null,
         ReadResourceRequest.builder(McpConsts.RESOURCE_STATS_URI).build());

      TextResourceContents text = assertInstanceOf(
         TextResourceContents.class, result.contents().get(0));
      assertTrue(text.text().contains("Total de arquivos: 1"));
      assertTrue(text.text().contains("Imagens: 1 arquivo(s)"));
   }

   @Test
   @DisplayName("HistoryResource (app://historico) deve listar lotes vazio e após organização")
   void historyResourceShouldReturnBatches() {
      HistoryResource res = new HistoryResource(composition.getOrganizationRepository());

      // Histórico inicialmente vazio
      ReadResourceResult emptyResult = res.read(null,
         ReadResourceRequest.builder(McpConsts.RESOURCE_HISTORY_URI).build());
      String emptyText = ((TextResourceContents) emptyResult.contents().get(0)).text();
      assertTrue(emptyText.contains("Total de lotes registrados: 0"));

      // Aplica um plano e verifica o histórico atualizado
      OrganizationPlan plan = composition.getOrganizationPlanner().createPlan(null, false);
      composition.getOrganizationExecutor().executePlan(plan.planId());

      ReadResourceResult populatedResult = res.read(null,
         ReadResourceRequest.builder(McpConsts.RESOURCE_HISTORY_URI).build());
      String populatedText = ((TextResourceContents) populatedResult.contents().get(0)).text();
      assertTrue(populatedText.contains("Total de lotes registrados: 1"));
      assertTrue(populatedText.contains("CONCLUIDO"));
   }

   @Test
   @DisplayName("ClassificationRulesResource (app://regras-classificacao) deve listar todas as categorias e extensões")
   void classificationRulesResourceShouldListCategoriesAndExtensions() {
      ClassificationRulesResource res =
         new ClassificationRulesResource(composition.getClassificationRules());
      ReadResourceResult result = res.read(null,
         ReadResourceRequest.builder(McpConsts.RESOURCE_RULES_URI).build());

      String text = ((TextResourceContents) result.contents().get(0)).text();
      assertTrue(text.contains("Imagens: jpg, jpeg, png"));
      assertTrue(text.contains("Design: psd, ai"));
      assertTrue(text.contains("Categoria padrão (sem extensão ou desconhecida): Outros"));
   }

   @Test
   @DisplayName("OrganizeFolderPrompt (organizar_pasta) deve orientar fluxo seguro com confirmação")
   void organizeFolderPromptShouldIncludeMandatoryConfirmationAndTools() {
      OrganizeFolderPrompt prompt = new OrganizeFolderPrompt();

      // Sem argumento opcional 'pasta' -> assume '.'
      GetPromptResult resDefault = prompt.run(null,
         GetPromptRequest.builder(McpConsts.PROMPT_ORGANIZE)
                         .arguments(Map.of())
                         .build());
      String textDefault = ((TextContent) resDefault.messages().get(0).content()).text();
      assertTrue(textDefault.contains(McpConsts.SCAN_TOOL_NAME));
      assertTrue(textDefault.contains(McpConsts.SUGGEST_ORG_TOOL_NAME));
      assertTrue(textDefault.contains(McpConsts.APPLY_ORG_TOOL_NAME));
      assertTrue(textDefault.contains("CONFIRMAÇÃO EXPLÍCITA"));

      // Com argumento 'pasta'
      GetPromptResult resCustom = prompt.run(null,
         GetPromptRequest.builder(McpConsts.PROMPT_ORGANIZE)
                         .arguments(Map.of("pasta", "downloads"))
                         .build());
      String textCustom = ((TextContent) resCustom.messages().get(0).content()).text();
      assertTrue(textCustom.contains("downloads"));
   }

   @Test
   @DisplayName("FreeSpacePrompt (liberar_espaco) deve orientar listar_maiores + encontrar_duplicados e proibir apagar")
   void freeSpacePromptShouldReferenceToolsAndForbidDeletion() {
      FreeSpacePrompt prompt = new FreeSpacePrompt();
      GetPromptResult res = prompt.run(null,
         GetPromptRequest.builder(McpConsts.PROMPT_FREE_SPACE)
                         .arguments(Map.of("pasta", "projetos"))
                         .build());

      String text = ((TextContent) res.messages().get(0).content()).text();
      assertTrue(text.contains(McpConsts.LARGEST_TOOL_NAME));
      assertTrue(text.contains(McpConsts.DUPLICATES_TOOL_NAME));
      assertTrue(text.contains("NUNCA apague"));
      assertTrue(text.contains("projetos"));
   }

   @Test
   @DisplayName("FindFilePrompt (encontrar_arquivo) deve exigir argumento 'descricao' e orientar buscar_arquivos")
   void findFilePromptShouldValidateRequiredDescription() {
      FindFilePrompt prompt = new FindFilePrompt();

      GetPromptResult res = prompt.run(null,
         GetPromptRequest.builder(McpConsts.PROMPT_FIND_FILE)
                         .arguments(Map.of("descricao", "contrato PDF do ano passado"))
                         .build());
      String text = ((TextContent) res.messages().get(0).content()).text();
      assertTrue(text.contains(McpConsts.SEARCH_TOOL_NAME));
      assertTrue(text.contains("contrato PDF do ano passado"));

      // Ausência do argumento obrigatório 'descricao' -> IllegalArgumentException
      assertThrows(IllegalArgumentException.class, () ->
         prompt.run(null, GetPromptRequest.builder(McpConsts.PROMPT_FIND_FILE)
                                          .arguments(Map.of())
                                          .build()));
      assertThrows(IllegalArgumentException.class, () ->
         prompt.run(null, GetPromptRequest.builder(McpConsts.PROMPT_FIND_FILE)
                                          .arguments(Map.of("descricao", "   "))
                                          .build()));
   }
}
