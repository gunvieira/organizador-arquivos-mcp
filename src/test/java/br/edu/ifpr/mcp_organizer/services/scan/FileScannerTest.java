package br.edu.ifpr.mcp_organizer.services.scan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import br.edu.ifpr.mcp_organizer.services.classification.ClassificationRules;
import br.edu.ifpr.mcp_organizer.services.classification.ExtensionClassifier;
import br.edu.ifpr.mcp_organizer.services.scan.FileScanner.ScanResult;
import br.edu.ifpr.mcp_organizer.services.security.SandboxPathResolver;

class FileScannerTest {

   @TempDir
   Path tempDir;

   private SandboxPathResolver resolver;
   private ClassificationRules rules;
   private ExtensionClassifier classifier;
   private FileScanner scanner;

   @BeforeEach
   void setUp() {
      resolver   = new SandboxPathResolver(tempDir);
      rules      = ClassificationRules.loadDefault();
      classifier = new ExtensionClassifier(rules);
      scanner    = new FileScanner(resolver, classifier, rules);
   }

   @Test
   @DisplayName("Deve escanear pasta recursivamente e ignorar .mcp-organizer")
   void shouldScanRecursivelyAndIgnoreAppDataDir() throws IOException {
      Files.writeString(tempDir.resolve("foto.jpg"), "12345");
      Path sub = Files.createDirectories(tempDir.resolve("sub"));
      Files.writeString(sub.resolve("doc.pdf"), "1234567890");

      // Cria arquivo dentro de .mcp-organizer que deve ser ignorado
      Path appData = Files.createDirectories(resolver.getAppDataDir());
      Files.writeString(appData.resolve("plans.json"), "{}");

      ScanResult recursiveResult = scanner.scan(null, true);
      assertEquals(2, recursiveResult.files().size());
      assertFalse(recursiveResult.scanLimitReached());

      ScanResult nonRecursiveResult = scanner.scan(null, false);
      assertEquals(1, nonRecursiveResult.files().size());
      assertEquals("foto.jpg", nonRecursiveResult.files().get(0).fileName());
   }

   @Test
   @DisplayName("Deve gerar relatório textual completo com contagem e bytes por categoria")
   void shouldFormatScanReportWithCategoriesAndSample() throws IOException {
      Files.writeString(tempDir.resolve("imagem.png"), "abcd");
      Files.writeString(tempDir.resolve("planilha.xlsx"), "12345678");

      String report = scanner.formatScanReport(".", true);

      assertTrue(report.contains("Total de arquivos: 2"));
      assertTrue(report.contains("Imagens: 1 arquivo(s)"));
      assertTrue(report.contains("Planilhas: 1 arquivo(s)"));
      assertTrue(report.contains("Amostra de arquivos"));
      assertTrue(report.contains("imagem.png"));
      assertTrue(report.contains("planilha.xlsx"));
   }

   @Test
   @DisplayName("Deve avisar quando o limite de entradas examinadas for atingido")
   void shouldWarnWhenScanLimitReached() throws IOException {
      for (int i = 1; i <= 5; i++)
         Files.writeString(tempDir.resolve("arq" + i + ".txt"), "x");

      FileScanner smallLimitScanner = new FileScanner(
         resolver, classifier, rules, 3);

      String report = smallLimitScanner.formatScanReport(null, true);
      assertTrue(report.contains("AVISO: Limite de 3 entradas examinadas atingido"));
   }

   @Test
   @DisplayName("Deve informar quando diretório estiver vazio")
   void shouldReportEmptyDirectory() {
      String report = scanner.formatScanReport(null, true);
      assertTrue(report.contains("Total de arquivos: 0"));
      assertTrue(report.contains("Nenhum arquivo encontrado no diretório."));
   }

   @Test
   @DisplayName("Deve falhar ao escanear pasta inexistente ou fora da raiz")
   void shouldFailOnInvalidOrOutsideDirectory() {
      assertThrows(IllegalArgumentException.class,
                   () -> scanner.scan("nao_existe", true));
      assertThrows(IllegalArgumentException.class,
                   () -> scanner.scan("../fora", true));
   }

   @Test
   @DisplayName("FileInfo.formatBytes deve formatar B, KB, MB e GB corretamente")
   void shouldFormatBytesCorrectly() {
      assertEquals("500 B", FileInfo.formatBytes(500));
      assertEquals("2.00 KB", FileInfo.formatBytes(2048));
      assertEquals("1.00 MB", FileInfo.formatBytes(1024L * 1024L));
      assertEquals("1.00 GB", FileInfo.formatBytes(1024L * 1024L * 1024L));
   }
}
