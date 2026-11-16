package br.edu.ifpr.mcp_organizer.services.search;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import br.edu.ifpr.mcp_organizer.mcp.tools.ListLargestFilesTool;
import br.edu.ifpr.mcp_organizer.mcp.tools.ListOldestFilesTool;
import br.edu.ifpr.mcp_organizer.mcp.tools.ScanDirectoryTool;
import br.edu.ifpr.mcp_organizer.mcp.tools.SearchFilesTool;
import br.edu.ifpr.mcp_organizer.mcp.utils.McpConsts;
import br.edu.ifpr.mcp_organizer.services.classification.ClassificationRules;
import br.edu.ifpr.mcp_organizer.services.classification.ExtensionClassifier;
import br.edu.ifpr.mcp_organizer.services.scan.FileScanner;
import br.edu.ifpr.mcp_organizer.services.security.SandboxPathResolver;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;

class FileSearchServiceAndToolsTest {

   @TempDir
   Path tempDir;

   private FileScanner scanner;
   private FileSearchService searchService;

   @BeforeEach
   void setUp() throws IOException {
      SandboxPathResolver resolver = new SandboxPathResolver(tempDir);
      ClassificationRules rules    = ClassificationRules.loadDefault();
      ExtensionClassifier clf      = new ExtensionClassifier(rules);
      scanner                      = new FileScanner(resolver, clf, rules);
      searchService                = new FileSearchService(scanner, rules);

      // Cria estrutura de arquivos para teste em diretório temporário
      Path f1 = tempDir.resolve("contrato_2025.pdf");
      Files.writeString(f1, "1234567890"); // 10 bytes
      Files.setLastModifiedTime(f1, FileTime.from(Instant.parse("2025-01-15T10:00:00Z")));

      Path f2 = tempDir.resolve("foto_ferias.jpg");
      Files.writeString(f2, "123456789012345678901234567890"); // 30 bytes
      Files.setLastModifiedTime(f2, FileTime.from(Instant.parse("2026-03-10T12:00:00Z")));

      Path sub = Files.createDirectories(tempDir.resolve("subpasta"));
      Path f3 = sub.resolve("planilha_custos.xlsx");
      Files.writeString(f3, "12345678901234567890"); // 20 bytes
      Files.setLastModifiedTime(f3, FileTime.from(Instant.parse("2024-06-01T08:00:00Z")));
   }

   @Test
   @DisplayName("Deve buscar arquivos combinando glob, categoria, extensões, tamanho e datas")
   void shouldSearchWithMultipleFilters() {
      FileCriteria criteria = FileCriteria.builder()
         .nameGlob("contrato*")
         .category("Documentos")
         .extensions(List.of("pdf", "docx"))
         .minSize(5L)
         .maxSize(15L)
         .modifiedAfter(LocalDate.of(2025, 1, 1))
         .modifiedBefore(LocalDate.of(2025, 12, 31))
         .recursive(true)
         .maxResults(10)
         .build();

      String report = searchService.search(criteria);
      assertTrue(report.contains("Resultados encontrados: 1"));
      assertTrue(report.contains("contrato_2025.pdf"));
      assertFalse(report.contains("foto_ferias.jpg"));
   }

   @Test
   @DisplayName("Deve avisar quando max_results truncar a lista de resultados")
   void shouldWarnWhenMaxResultsReached() {
      FileCriteria criteria = FileCriteria.builder()
         .recursive(true)
         .maxResults(1)
         .build();

      String report = searchService.search(criteria);
      assertTrue(report.contains("Resultados encontrados: 1"));
      assertTrue(report.contains("AVISO: Limite de resultados (1) atingido"));
   }

   @Test
   @DisplayName("Deve rejeitar filtros inválidos (min_size > max_size, categoria inexistente, datas e limites)")
   void shouldRejectInvalidSearchCriteria() {
      // min_size > max_size
      assertThrows(IllegalArgumentException.class, () ->
         searchService.search(FileCriteria.builder().minSize(100L).maxSize(10L).build()));

      // categoria inexistente
      assertThrows(IllegalArgumentException.class, () ->
         searchService.search(FileCriteria.builder().category("NaoExiste").build()));

      // max_results fora de 1..100
      assertThrows(IllegalArgumentException.class, () ->
         searchService.search(FileCriteria.builder().maxResults(0).build()));
      assertThrows(IllegalArgumentException.class, () ->
         searchService.search(FileCriteria.builder().maxResults(101).build()));

      // modified_after > modified_before
      assertThrows(IllegalArgumentException.class, () ->
         searchService.search(FileCriteria.builder()
            .modifiedAfter(LocalDate.of(2026, 5, 1))
            .modifiedBefore(LocalDate.of(2026, 1, 1))
            .build()));

      // glob com diretório
      assertThrows(IllegalArgumentException.class, () ->
         searchService.search(FileCriteria.builder().nameGlob("sub/*.pdf").build()));

      // data mal formada
      assertThrows(IllegalArgumentException.class, () ->
         FileSearchService.parseIsoDate("15/01/2026", "modified_after"));
   }

   @Test
   @DisplayName("Deve listar maiores arquivos em ordem decrescente")
   void shouldListLargestFilesDescending() {
      String report = searchService.listLargest(null, 2, true);

      assertTrue(report.contains("Total exibido: 2"));
      int idxFoto = report.indexOf("foto_ferias.jpg");
      int idxPlanilha = report.indexOf("planilha_custos.xlsx");
      assertTrue(idxFoto > 0 && idxPlanilha > idxFoto);
      assertFalse(report.contains("contrato_2025.pdf"));
   }

   @Test
   @DisplayName("Deve listar arquivos mais antigos em ordem crescente de data")
   void shouldListOldestFilesAscending() {
      String report = searchService.listOldest(null, 2, true);

      assertTrue(report.contains("Total exibido: 2"));
      int idxPlanilha = report.indexOf("planilha_custos.xlsx"); // 2024
      int idxContrato = report.indexOf("contrato_2025.pdf");    // 2025
      assertTrue(idxPlanilha > 0 && idxContrato > idxPlanilha);
   }

   @Test
   @DisplayName("Tools de leitura devem retornar sucesso ou isError=true em caso de falha")
   void readingToolsShouldHandleSuccessAndErrors() {
      ScanDirectoryTool scanTool         = new ScanDirectoryTool(scanner);
      SearchFilesTool searchTool         = new SearchFilesTool(searchService);
      ListLargestFilesTool largestTool   = new ListLargestFilesTool(searchService);
      ListOldestFilesTool oldestTool     = new ListOldestFilesTool(searchService);

      // 1. escanear_pasta sucesso e erro
      CallToolResult scanOk = scanTool.run(null,
         CallToolRequest.builder(McpConsts.SCAN_TOOL_NAME)
                        .arguments(Map.of("recursive", true))
                        .build());
      assertFalse(scanOk.isError());

      CallToolResult scanErr = scanTool.run(null,
         CallToolRequest.builder(McpConsts.SCAN_TOOL_NAME)
                        .arguments(Map.of("path", "../invalido"))
                        .build());
      assertTrue(scanErr.isError());

      // 2. buscar_arquivos sucesso e erro
      CallToolResult searchOk = searchTool.run(null,
         CallToolRequest.builder(McpConsts.SEARCH_TOOL_NAME)
                        .arguments(Map.of("category", "Imagens"))
                        .build());
      assertFalse(searchOk.isError());
      assertTrue(((TextContent) searchOk.content().get(0)).text().contains("foto_ferias.jpg"));

      CallToolResult searchErr = searchTool.run(null,
         CallToolRequest.builder(McpConsts.SEARCH_TOOL_NAME)
                        .arguments(Map.of("modified_after", "data-invalida"))
                        .build());
      assertTrue(searchErr.isError());

      // 3. listar_maiores sucesso e erro
      CallToolResult largestOk = largestTool.run(null,
         CallToolRequest.builder(McpConsts.LARGEST_TOOL_NAME)
                        .arguments(Map.of("limit", 5))
                        .build());
      assertFalse(largestOk.isError());

      CallToolResult largestErr = largestTool.run(null,
         CallToolRequest.builder(McpConsts.LARGEST_TOOL_NAME)
                        .arguments(Map.of("limit", 500))
                        .build());
      assertTrue(largestErr.isError());

      // 4. listar_mais_antigos sucesso e erro
      CallToolResult oldestOk = oldestTool.run(null,
         CallToolRequest.builder(McpConsts.OLDEST_TOOL_NAME)
                        .arguments(Map.of("limit", 5))
                        .build());
      assertFalse(oldestOk.isError());
      TextContent oldestText = assertInstanceOf(TextContent.class, oldestOk.content().get(0));
      assertTrue(oldestText.text().contains("planilha_custos.xlsx"));

      CallToolResult oldestErr = oldestTool.run(null,
         CallToolRequest.builder(McpConsts.OLDEST_TOOL_NAME)
                        .arguments(Map.of("limit", 0))
                        .build());
      assertTrue(oldestErr.isError());
   }
}
