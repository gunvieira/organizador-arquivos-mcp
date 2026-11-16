package br.edu.ifpr.mcp_organizer.services.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import br.edu.ifpr.mcp_organizer.main.AppConsts;

class SandboxPathResolverTest {

   @TempDir
   Path tempDir;

   @AfterEach
   void tearDown() {
      System.clearProperty(AppConsts.AUTH_DIR_PROP);
   }

   @Test
   @DisplayName("Deve rejeitar raiz nula")
   void shouldRejectNullRoot() {
      assertThrows(IllegalArgumentException.class,
                   () -> new SandboxPathResolver(null));
   }

   @Test
   @DisplayName("Deve rejeitar raiz inexistente")
   void shouldRejectNonExistentRoot() {
      Path nonExistent = tempDir.resolve("nao_existe");
      assertThrows(IllegalArgumentException.class,
                   () -> new SandboxPathResolver(nonExistent));
   }

   @Test
   @DisplayName("Deve rejeitar raiz que é um arquivo regular")
   void shouldRejectFileAsRoot() throws IOException {
      Path file = Files.createFile(tempDir.resolve("arquivo.txt"));
      assertThrows(IllegalArgumentException.class,
                   () -> new SandboxPathResolver(file));
   }

   @Test
   @DisplayName("Deve aceitar diretório válido como raiz")
   void shouldAcceptValidDirectoryRoot() throws IOException {
      SandboxPathResolver resolver = new SandboxPathResolver(tempDir);
      assertEquals(tempDir.toRealPath(), resolver.getRoot());
      assertEquals(tempDir.toRealPath().resolve(".mcp-organizer"),
                   resolver.getAppDataDir());
   }

   @Test
   @DisplayName("Deve carregar da configuração priorizando propriedade sobre variável")
   void shouldCreateFromConfiguration() throws IOException {
      SandboxPathResolver resolver = SandboxPathResolver.fromConfiguration(
         tempDir.toString(), null);
      assertEquals(tempDir.toRealPath(), resolver.getRoot());

      SandboxPathResolver resolverFromEnv = SandboxPathResolver.fromConfiguration(
         "", tempDir.toString());
      assertEquals(tempDir.toRealPath(), resolverFromEnv.getRoot());
   }

   @Test
   @DisplayName("Deve lançar IllegalStateException quando nenhuma configuração for informada")
   void shouldFailWhenConfigurationMissing() {
      assertThrows(IllegalStateException.class,
                   () -> SandboxPathResolver.fromConfiguration("", ""));
      assertThrows(IllegalStateException.class,
                   () -> SandboxPathResolver.fromConfiguration(null, "   "));
   }

   @ParameterizedTest(name = "Deve resolver entrada vazia ou ponto \"{0}\" para a raiz")
   @NullAndEmptySource
   @ValueSource(strings = { "   ", "." })
   void shouldResolveEmptyOrDotToRoot(String input) {
      SandboxPathResolver resolver = new SandboxPathResolver(tempDir);
      assertEquals(resolver.getRoot(), resolver.resolve(input));
   }

   @Test
   @DisplayName("Deve resolver subcaminho relativo válido dentro da raiz")
   void shouldResolveValidRelativePath() throws IOException {
      SandboxPathResolver resolver = new SandboxPathResolver(tempDir);
      Path subDir = Files.createDirectories(tempDir.resolve("pasta/sub"));
      Path resolved = resolver.resolve("pasta/sub");
      assertEquals(subDir.toRealPath(), resolved);
   }

   @ParameterizedTest(name = "Deve bloquear tentativa de path traversal: {0}")
   @ValueSource(strings = {
      "..",
      "../fora",
      "..\\fora",
      "sub/../../fora",
      "sub/..",
      "a/b/../../../escape"
   })
   void shouldRejectPathTraversal(String maliciousPath) {
      SandboxPathResolver resolver = new SandboxPathResolver(tempDir);
      assertThrows(IllegalArgumentException.class,
                   () -> resolver.resolve(maliciousPath));
   }

   @ParameterizedTest(name = "Deve bloquear caminho absoluto: {0}")
   @ValueSource(strings = {
      "/etc/passwd",
      "\\Windows\\System32",
      "C:\\Windows",
      "D:/dados"
   })
   void shouldRejectAbsolutePaths(String absolutePath) {
      SandboxPathResolver resolver = new SandboxPathResolver(tempDir);
      assertThrows(IllegalArgumentException.class,
                   () -> resolver.resolve(absolutePath));
   }

   @ParameterizedTest(name = "Deve bloquear acesso direto à pasta reservada .mcp-organizer: {0}")
   @ValueSource(strings = {
      ".mcp-organizer",
      ".mcp-organizer/plans.json",
      ".MCP-ORGANIZER/history.json"
   })
   void shouldRejectAppDataFolderAccess(String reservedPath) {
      SandboxPathResolver resolver = new SandboxPathResolver(tempDir);
      assertThrows(IllegalArgumentException.class,
                   () -> resolver.resolve(reservedPath));
   }

   @Test
   @DisplayName("Deve validar diretório e arquivo existentes")
   void shouldResolveExistingDirectoryAndFile() throws IOException {
      SandboxPathResolver resolver = new SandboxPathResolver(tempDir);
      Files.createDirectory(tempDir.resolve("docs"));
      Files.writeString(tempDir.resolve("docs/nota.txt"), "conteudo");

      Path dir = resolver.resolveExistingDirectory("docs");
      assertTrue(Files.isDirectory(dir));

      Path file = resolver.resolveExistingFile("docs/nota.txt");
      assertTrue(Files.isRegularFile(file));

      // erro quando diretório não existe ou é arquivo
      assertThrows(IllegalArgumentException.class,
                   () -> resolver.resolveExistingDirectory("inexistente"));
      assertThrows(IllegalArgumentException.class,
                   () -> resolver.resolveExistingDirectory("docs/nota.txt"));

      // erro quando arquivo não existe ou é diretório
      assertThrows(IllegalArgumentException.class,
                   () -> resolver.resolveExistingFile("docs/inexistente.txt"));
      assertThrows(IllegalArgumentException.class,
                   () -> resolver.resolveExistingFile("docs"));
      assertThrows(IllegalArgumentException.class,
                   () -> resolver.resolveExistingFile(""));
   }

   @Test
   @DisplayName("Deve relativizar caminhos dentro da raiz padronizando barra '/'")
   void shouldRelativizePathsInsideRoot() throws IOException {
      SandboxPathResolver resolver = new SandboxPathResolver(tempDir);
      Path nested = resolver.resolve("Imagens/foto.jpg");

      assertEquals(".", resolver.relativize(resolver.getRoot()));
      assertEquals("Imagens/foto.jpg", resolver.relativize(nested));
      assertThrows(IllegalArgumentException.class,
                   () -> resolver.relativize(null));
      assertThrows(IllegalArgumentException.class,
                   () -> resolver.relativize(tempDir.getParent()));
   }

   @Test
   @DisplayName("Deve identificar corretamente caminhos pertencentes a .mcp-organizer")
   void shouldIdentifyAppDataPath() {
      SandboxPathResolver resolver = new SandboxPathResolver(tempDir);
      assertTrue(resolver.isAppDataPath(resolver.getAppDataDir()));
      assertTrue(resolver.isAppDataPath(
         resolver.getAppDataDir().resolve("plans.json")));
      assertFalse(resolver.isAppDataPath(resolver.getRoot().resolve("foto.png")));
      assertFalse(resolver.isAppDataPath(null));
   }

   @Test
   @DisplayName("Deve carregar de propriedade de sistema via fromConfiguration()")
   void shouldLoadFromSystemProperty() throws IOException {
      System.setProperty(AppConsts.AUTH_DIR_PROP, tempDir.toString());
      SandboxPathResolver resolver = SandboxPathResolver.fromConfiguration();
      assertNotNull(resolver);
      assertEquals(tempDir.toRealPath(), resolver.getRoot());
   }
}
