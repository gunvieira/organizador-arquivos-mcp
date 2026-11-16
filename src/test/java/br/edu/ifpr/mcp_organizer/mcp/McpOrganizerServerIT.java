package br.edu.ifpr.mcp_organizer.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import br.edu.ifpr.mcp_organizer.main.AppConsts;
import br.edu.ifpr.mcp_organizer.mcp.utils.McpConsts;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapper;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.GetPromptRequest;
import io.modelcontextprotocol.spec.McpSchema.GetPromptResult;
import io.modelcontextprotocol.spec.McpSchema.ListPromptsResult;
import io.modelcontextprotocol.spec.McpSchema.ListResourcesResult;
import io.modelcontextprotocol.spec.McpSchema.ListToolsResult;
import io.modelcontextprotocol.spec.McpSchema.Prompt;
import io.modelcontextprotocol.spec.McpSchema.ReadResourceRequest;
import io.modelcontextprotocol.spec.McpSchema.ReadResourceResult;
import io.modelcontextprotocol.spec.McpSchema.Resource;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.TextResourceContents;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import tools.jackson.databind.json.JsonMapper;

/**
 * Teste de integração real ({@code *IT}) executado pelo {@code maven-failsafe-plugin}
 * no perfil {@code server} ({@code mvn clean verify -Pserver}). Sobe o processo
 * do JAR real do servidor ({@code server/mcp_organizer_server.jar}) via
 * {@link StdioClientTransport} apontando {@code MCP_FILES_ROOT} para um diretório
 * temporário criado e removido automaticamente pelo próprio teste.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class McpOrganizerServerIT {

   private static final Logger LOG =
      LoggerFactory.getLogger(McpOrganizerServerIT.class);

   private static final String SERVER_PATH = "server/mcp_organizer_server.jar";
   private static final Pattern PLAN_ID_PATTERN =
      Pattern.compile("plan_id:\\s*([0-9a-fA-F\\-]{36})");

   private static Path tempRoot;
   private static McpSyncClient client;

   @BeforeAll
   static void setUpBeforeClass() throws Exception {
      // Cria um diretório temporário isolado (nunca usa caminhos fixos da máquina)
      tempRoot = Files.createTempDirectory("mcp-organizer-it-");
      seedTestFiles(tempRoot);

      File serverJar = new File(SERVER_PATH);
      assertTrue(serverJar.isFile(),
                 "JAR do servidor não encontrado: " +
                 serverJar.getAbsolutePath());

      String jarPath = serverJar.getAbsolutePath();
      ServerParameters params = ServerParameters.builder("java")
         .args("-jar", jarPath)
         .addEnvVar(AppConsts.AUTH_DIR_VAR, tempRoot.toString())
         .build();

      JacksonMcpJsonMapper jsonMapper = new JacksonMcpJsonMapper(new JsonMapper());
      StdioClientTransport transport  = new StdioClientTransport(params, jsonMapper);

      transport.setStdErrorHandler(line -> LOG.info("[SERVER STDERR] {}", line));

      client = McpClient.sync(transport).build();
      client.initialize();
   }

   @AfterAll
   static void tearDownAfterClass() throws Exception {
      if (client != null)
         client.closeGracefully();

      if (tempRoot != null && Files.exists(tempRoot)) {
         try (Stream<Path> walk = Files.walk(tempRoot)) {
            walk.sorted(Comparator.reverseOrder())
                .forEach(p -> {
                   try { Files.deleteIfExists(p); }
                   catch (IOException ignored) {}
                });
         }
      }
   }

   private static void seedTestFiles(Path root) throws IOException {
      // Dois arquivos duplicados (mesmo conteúdo)
      byte[] dupContent = "conteudo_duplicado_para_it_123456".getBytes();
      Path dup1 = Files.write(root.resolve("relatorio_2025.pdf"), dupContent);
      Files.setLastModifiedTime(dup1, FileTime.from(Instant.parse("2025-02-10T10:00:00Z")));

      Path sub = Files.createDirectories(root.resolve("subpasta"));
      Path dup2 = Files.write(sub.resolve("relatorio_copia.pdf"), dupContent);
      Files.setLastModifiedTime(dup2, FileTime.from(Instant.parse("2025-03-10T10:00:00Z")));

      // Um arquivo maior e mais antigo
      Path img = Files.writeString(
         root.resolve("foto_evento.jpg"),
         "A".repeat(200));
      Files.setLastModifiedTime(img, FileTime.from(Instant.parse("2023-05-01T08:00:00Z")));

      // Um arquivo vazio (0 bytes) que deve ser ignorado em duplicados
      Files.createFile(root.resolve("vazio.txt"));
   }

   @Test
   @Order(1)
   @DisplayName("Servidor deve publicar todas as 9 ferramentas com descrição e inputSchema")
   void shouldListAllExpectedTools() {
      ListToolsResult result = client.listTools();
      assertNotNull(result);
      assertEquals(9, result.tools().size());

      List<String> expectedNames = List.of(
         McpConsts.APT_TOOL_NAME,
         McpConsts.SCAN_TOOL_NAME,
         McpConsts.SEARCH_TOOL_NAME,
         McpConsts.DUPLICATES_TOOL_NAME,
         McpConsts.LARGEST_TOOL_NAME,
         McpConsts.OLDEST_TOOL_NAME,
         McpConsts.SUGGEST_ORG_TOOL_NAME,
         McpConsts.APPLY_ORG_TOOL_NAME,
         McpConsts.UNDO_ORG_TOOL_NAME
      );

      for (String expected : expectedNames) {
         Tool found = result.tools().stream()
            .filter(t -> expected.equals(t.name()))
            .findFirst()
            .orElse(null);
         assertNotNull(found, "Ferramenta não encontrada: " + expected);
         assertNotNull(found.description());
         assertNotNull(found.inputSchema());
      }
   }

   @Test
   @Order(2)
   @DisplayName("Tool 'auth_path' deve retornar o diretório temporário autorizado")
   void shouldExecuteAuthPathTool() {
      CallToolResult res = callTool(McpConsts.APT_TOOL_NAME, Map.of());
      assertFalse(res.isError());
      String text = firstText(res);
      assertTrue(text.contains(tempRoot.getFileName().toString()));
   }

   @Test
   @Order(3)
   @DisplayName("Tool 'escanear_pasta' deve ter sucesso para caminho válido e erro para caminho fora da raiz")
   void shouldExecuteScanDirectoryToolSuccessAndError() {
      CallToolResult ok = callTool(McpConsts.SCAN_TOOL_NAME,
                                   Map.of("recursive", true));
      assertFalse(ok.isError());
      String text = firstText(ok);
      assertTrue(text.contains("Total de arquivos: 4"));
      assertTrue(text.contains("Documentos: 3 arquivo(s)"));
      assertTrue(text.contains("Imagens: 1 arquivo(s)"));

      CallToolResult err = callTool(McpConsts.SCAN_TOOL_NAME,
                                    Map.of("path", "../fora_da_raiz"));
      assertTrue(err.isError());
   }

   @Test
   @Order(4)
   @DisplayName("Tool 'buscar_arquivos' deve filtrar arquivos no sucesso e retornar erro para filtro inválido")
   void shouldExecuteSearchFilesToolSuccessAndError() {
      CallToolResult ok = callTool(McpConsts.SEARCH_TOOL_NAME, Map.of(
         "name", "*.pdf",
         "category", "Documentos",
         "recursive", true,
         "max_results", 10
      ));
      assertFalse(ok.isError());
      String text = firstText(ok);
      assertTrue(text.contains("Resultados encontrados: 2"));
      assertTrue(text.contains("relatorio_2025.pdf"));

      CallToolResult err = callTool(McpConsts.SEARCH_TOOL_NAME, Map.of(
         "min_size", 500,
         "max_size", 10
      ));
      assertTrue(err.isError());
   }

   @Test
   @Order(5)
   @DisplayName("Tool 'encontrar_duplicados' deve identificar duplicados no sucesso e erro para caminho inválido")
   void shouldExecuteFindDuplicatesToolSuccessAndError() {
      CallToolResult ok = callTool(McpConsts.DUPLICATES_TOOL_NAME, Map.of(
         "recursive", true,
         "min_size", 1
      ));
      assertFalse(ok.isError());
      String text = firstText(ok);
      assertTrue(text.contains("Grupos de duplicados encontrados: 1"));
      assertTrue(text.contains("relatorio_2025.pdf"));
      assertTrue(text.contains("relatorio_copia.pdf"));

      CallToolResult err = callTool(McpConsts.DUPLICATES_TOOL_NAME, Map.of(
         "path", "pasta_inexistente"
      ));
      assertTrue(err.isError());
   }

   @Test
   @Order(6)
   @DisplayName("Tools 'listar_maiores' e 'listar_mais_antigos' devem ordenar corretamente e validar erros")
   void shouldExecuteLargestAndOldestToolsSuccessAndError() {
      // listar_maiores
      CallToolResult largestOk = callTool(McpConsts.LARGEST_TOOL_NAME, Map.of(
         "limit", 2,
         "recursive", true
      ));
      assertFalse(largestOk.isError());
      assertTrue(firstText(largestOk).contains("1. foto_evento.jpg"));

      CallToolResult largestErr = callTool(McpConsts.LARGEST_TOOL_NAME, Map.of(
         "limit", 999
      ));
      assertTrue(largestErr.isError());

      // listar_mais_antigos
      CallToolResult oldestOk = callTool(McpConsts.OLDEST_TOOL_NAME, Map.of(
         "limit", 2,
         "recursive", true
      ));
      assertFalse(oldestOk.isError());
      assertTrue(firstText(oldestOk).contains("1. foto_evento.jpg"));

      CallToolResult oldestErr = callTool(McpConsts.OLDEST_TOOL_NAME, Map.of(
         "limit", 0
      ));
      assertTrue(oldestErr.isError());
   }

   @Test
   @Order(7)
   @DisplayName("Tools de organização ('sugerir_organizacao' -> 'aplicar_organizacao' -> 'desfazer_organizacao') devem funcionar no ciclo completo e tratar erros")
   void shouldExecuteOrganizationToolsLifecycleSuccessAndError() {
      // 1. Tentar desfazer antes de qualquer organização -> erro
      CallToolResult undoBefore = callTool(McpConsts.UNDO_ORG_TOOL_NAME, Map.of());
      assertTrue(undoBefore.isError());

      // 2. Tentar sugerir organização em pasta inválida -> erro
      CallToolResult suggestErr = callTool(McpConsts.SUGGEST_ORG_TOOL_NAME,
                                           Map.of("path", "../escape"));
      assertTrue(suggestErr.isError());

      // 3. Sugerir organização na raiz (não recursiva) -> gera plan_id sem mover nada
      CallToolResult suggestOk = callTool(McpConsts.SUGGEST_ORG_TOOL_NAME,
                                          Map.of("recursive", false));
      assertFalse(suggestOk.isError());
      String suggestText = firstText(suggestOk);
      Matcher matcher = PLAN_ID_PATTERN.matcher(suggestText);
      assertTrue(matcher.find(), "plan_id não encontrado no relatório: " + suggestText);
      String planId = matcher.group(1);

      // Arquivos continuam na raiz antes de aplicar
      assertTrue(Files.exists(tempRoot.resolve("foto_evento.jpg")));
      assertFalse(Files.exists(tempRoot.resolve("Imagens/foto_evento.jpg")));

      // 4. Aplicar plano com plan_id inexistente -> erro
      CallToolResult applyNotFound = callTool(McpConsts.APPLY_ORG_TOOL_NAME,
                                              Map.of("plan_id", "00000000-0000-0000-0000-000000000000"));
      assertTrue(applyNotFound.isError());

      // 5. Aplicar plano válido -> move os 3 arquivos da raiz para suas categorias
      CallToolResult applyOk = callTool(McpConsts.APPLY_ORG_TOOL_NAME,
                                        Map.of("plan_id", planId));
      assertFalse(applyOk.isError());
      assertTrue(firstText(applyOk).contains("Arquivos movidos com sucesso: 3"));
      assertTrue(Files.exists(tempRoot.resolve("Imagens/foto_evento.jpg")));
      assertTrue(Files.exists(tempRoot.resolve("Documentos/relatorio_2025.pdf")));

      // 6. Tentar aplicar o mesmo plano pela segunda vez -> erro
      CallToolResult applyTwice = callTool(McpConsts.APPLY_ORG_TOOL_NAME,
                                           Map.of("plan_id", planId));
      assertTrue(applyTwice.isError());

      // 7. Desfazer organização -> restaura todos os arquivos aos locais originais
      CallToolResult undoOk = callTool(McpConsts.UNDO_ORG_TOOL_NAME, Map.of());
      assertFalse(undoOk.isError());
      assertTrue(firstText(undoOk).contains("Arquivos revertidos com sucesso: 3"));
      assertTrue(Files.exists(tempRoot.resolve("foto_evento.jpg")));
      assertTrue(Files.exists(tempRoot.resolve("relatorio_2025.pdf")));
   }

   @Test
   @Order(8)
   @DisplayName("Servidor deve listar e ler todos os 5 resources ('app://...')")
   void shouldListAndReadAllResources() {
      ListResourcesResult listRes = client.listResources();
      assertNotNull(listRes);
      assertEquals(5, listRes.resources().size());

      List<String> expectedUris = List.of(
         McpConsts.RESOURCE_ABOUT_URI,
         McpConsts.RESOURCE_ROOT_INFO_URI,
         McpConsts.RESOURCE_STATS_URI,
         McpConsts.RESOURCE_HISTORY_URI,
         McpConsts.RESOURCE_RULES_URI
      );

      for (String uri : expectedUris) {
         assertTrue(listRes.resources().stream()
                           .map(Resource::uri)
                           .anyMatch(uri::equals),
                    "Resource não listado: " + uri);

         ReadResourceResult readRes = client.readResource(
            ReadResourceRequest.builder(uri).build());
         assertNotNull(readRes);
         assertFalse(readRes.contents().isEmpty());
         TextResourceContents content = assertInstanceOf(
            TextResourceContents.class, readRes.contents().get(0));
         assertFalse(content.text().isBlank());
      }
   }

   @Test
   @Order(9)
   @DisplayName("Servidor deve listar e obter todos os 3 prompts")
   void shouldListAndGetAllPrompts() {
      ListPromptsResult listPrompts = client.listPrompts();
      assertNotNull(listPrompts);
      assertEquals(3, listPrompts.prompts().size());

      List<String> expectedPrompts = List.of(
         McpConsts.PROMPT_ORGANIZE,
         McpConsts.PROMPT_FREE_SPACE,
         McpConsts.PROMPT_FIND_FILE
      );

      for (String name : expectedPrompts) {
         assertTrue(listPrompts.prompts().stream()
                               .map(Prompt::name)
                               .anyMatch(name::equals),
                    "Prompt não listado: " + name);
      }

      // 1. organizar_pasta
      GetPromptResult orgPrompt = client.getPrompt(
         GetPromptRequest.builder(McpConsts.PROMPT_ORGANIZE)
                         .arguments(Map.of("pasta", "."))
                         .build());
      assertFalse(orgPrompt.messages().isEmpty());

      // 2. liberar_espaco
      GetPromptResult freePrompt = client.getPrompt(
         GetPromptRequest.builder(McpConsts.PROMPT_FREE_SPACE)
                         .arguments(Map.of("pasta", "."))
                         .build());
      assertFalse(freePrompt.messages().isEmpty());

      // 3. encontrar_arquivo (com argumento obrigatório 'descricao')
      GetPromptResult findPrompt = client.getPrompt(
         GetPromptRequest.builder(McpConsts.PROMPT_FIND_FILE)
                         .arguments(Map.of("descricao", "relatório em PDF"))
                         .build());
      assertFalse(findPrompt.messages().isEmpty());

      // 4. encontrar_arquivo sem argumento obrigatório -> erro no protocolo
      assertThrows(Exception.class, () ->
         client.getPrompt(
            GetPromptRequest.builder(McpConsts.PROMPT_FIND_FILE)
                            .arguments(Map.of())
                            .build()));
   }

   private static CallToolResult callTool(String name, Map<String, Object> args) {
      CallToolRequest req = CallToolRequest.builder(name)
                                           .arguments(args)
                                           .build();
      CallToolResult res = client.callTool(req);
      assertNotNull(res);
      return res;
   }

   private static String firstText(CallToolResult res) {
      assertNotNull(res.content());
      assertFalse(res.content().isEmpty());
      TextContent text = assertInstanceOf(TextContent.class, res.content().get(0));
      return text.text();
   }
}
