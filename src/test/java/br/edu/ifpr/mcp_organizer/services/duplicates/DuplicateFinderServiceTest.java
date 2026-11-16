package br.edu.ifpr.mcp_organizer.services.duplicates;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import br.edu.ifpr.mcp_organizer.mcp.tools.FindDuplicatesTool;
import br.edu.ifpr.mcp_organizer.mcp.utils.McpConsts;
import br.edu.ifpr.mcp_organizer.services.classification.ClassificationRules;
import br.edu.ifpr.mcp_organizer.services.classification.ExtensionClassifier;
import br.edu.ifpr.mcp_organizer.services.duplicates.DuplicateFinderService.DuplicateReportData;
import br.edu.ifpr.mcp_organizer.services.scan.FileScanner;
import br.edu.ifpr.mcp_organizer.services.security.SandboxPathResolver;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;

class DuplicateFinderServiceTest {

   @TempDir
   Path tempDir;

   private Sha256HashStrategy hashStrategy;
   private DuplicateFinderService duplicateService;

   @BeforeEach
   void setUp() {
      SandboxPathResolver resolver = new SandboxPathResolver(tempDir);
      ClassificationRules rules    = ClassificationRules.loadDefault();
      ExtensionClassifier clf      = new ExtensionClassifier(rules);
      FileScanner scanner          = new FileScanner(resolver, clf, rules);
      hashStrategy                 = new Sha256HashStrategy();
      duplicateService             = new DuplicateFinderService(scanner, hashStrategy);
   }

   @Test
   @DisplayName("Deve ignorar arquivos de 0 bytes e encontrar duplicados pequenos")
   void shouldIgnoreZeroByteFilesAndFindSmallDuplicates() throws IOException {
      // Dois arquivos vazios (0 bytes) -> devem ser ignorados
      Files.createFile(tempDir.resolve("vazio1.txt"));
      Files.createFile(tempDir.resolve("vazio2.txt"));

      // Três cópias idênticas de 20 bytes -> 2 cópias extras = 40 bytes desperdiçados
      byte[] content = "conteudo_duplicado_1".getBytes();
      Files.write(tempDir.resolve("copia1.txt"), content);
      Files.write(tempDir.resolve("copia2.txt"), content);
      Path sub = Files.createDirectories(tempDir.resolve("sub"));
      Files.write(sub.resolve("copia3.txt"), content);

      // Arquivo de mesmo tamanho (20 bytes) mas conteúdo diferente
      Files.write(tempDir.resolve("diferente.txt"), "conteudo_diferente_2".getBytes());

      DuplicateReportData data = duplicateService.findDuplicates(null, true, 0L);

      assertEquals(1, data.groups().size());
      assertEquals(3, data.groups().get(0).files().size());
      assertEquals(40L, data.totalWastedBytes());
   }

   @Test
   @DisplayName("Deve distinguir arquivos maiores que 64 KB que empatam no hash parcial mas diferem no final")
   void shouldDistinguishLargeFilesWithSamePrefixButDifferentSuffix() throws IOException {
      int size = DuplicateFinderService.PARTIAL_HASH_BYTES + 4096; // 68 KB
      byte[] base = new byte[size];
      Arrays.fill(base, (byte) 'A');

      byte[] variant = Arrays.copyOf(base, size);
      variant[size - 1] = (byte) 'B'; // difere apenas no último byte após 64 KB

      Path large1 = tempDir.resolve("grande1.bin");
      Path large2 = tempDir.resolve("grande2_igual.bin");
      Path large3 = tempDir.resolve("grande3_diferente_no_fim.bin");

      Files.write(large1, base);
      Files.write(large2, base);
      Files.write(large3, variant);

      // Os 3 arquivos têm o mesmo hash parcial (64 KB), mas apenas large1 e large2 têm o mesmo SHA-256 completo
      assertEquals(
         hashStrategy.computePartialHash(large1, DuplicateFinderService.PARTIAL_HASH_BYTES),
         hashStrategy.computePartialHash(large3, DuplicateFinderService.PARTIAL_HASH_BYTES)
      );
      assertNotEquals(
         hashStrategy.computeFullHash(large1),
         hashStrategy.computeFullHash(large3)
      );

      DuplicateReportData data = duplicateService.findDuplicates(null, true, 1L);
      assertEquals(1, data.groups().size());
      assertEquals(2, data.groups().get(0).files().size());
      assertEquals(size, data.totalWastedBytes());
   }

   @Test
   @DisplayName("Deve validar argumentos de Sha256HashStrategy e DuplicateFinderService")
   void shouldValidateArguments() {
      assertThrows(IllegalArgumentException.class,
                   () -> hashStrategy.computePartialHash(null, 10));
      assertThrows(IllegalArgumentException.class,
                   () -> hashStrategy.computePartialHash(tempDir, 0));
      assertThrows(IllegalArgumentException.class,
                   () -> hashStrategy.computeFullHash(null));
      assertThrows(IllegalArgumentException.class,
                   () -> duplicateService.findDuplicates(null, true, -1L));
   }

   @Test
   @DisplayName("FindDuplicatesTool deve retornar relatório formatado no sucesso e erro em caminho inválido")
   void findDuplicatesToolShouldHandleSuccessAndError() throws IOException {
      byte[] data = "duplicado_teste".getBytes();
      Files.write(tempDir.resolve("a.dat"), data);
      Files.write(tempDir.resolve("b.dat"), data);

      FindDuplicatesTool tool = new FindDuplicatesTool(duplicateService);

      CallToolResult ok = tool.run(null,
         CallToolRequest.builder(McpConsts.DUPLICATES_TOOL_NAME)
                        .arguments(Map.of("recursive", true, "min_size", 1))
                        .build());
      assertFalse(ok.isError());
      String text = ((TextContent) ok.content().get(0)).text();
      assertTrue(text.contains("Grupos de duplicados encontrados: 1"));
      assertTrue(text.contains("a.dat"));
      assertTrue(text.contains("b.dat"));

      CallToolResult err = tool.run(null,
         CallToolRequest.builder(McpConsts.DUPLICATES_TOOL_NAME)
                        .arguments(Map.of("path", "../escape"))
                        .build());
      assertTrue(err.isError());
   }
}
