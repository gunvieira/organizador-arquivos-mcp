package br.edu.ifpr.mcp_organizer.mcp.tools;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import br.edu.ifpr.mcp_organizer.main.ServerComposition;
import br.edu.ifpr.mcp_organizer.mcp.resources.AboutResource;
import br.edu.ifpr.mcp_organizer.mcp.resources.RootInfoResource;
import br.edu.ifpr.mcp_organizer.mcp.utils.McpConsts;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.ReadResourceRequest;
import io.modelcontextprotocol.spec.McpSchema.ReadResourceResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.TextResourceContents;

class BaseToolsAndResourcesTest {

   @TempDir
   Path tempDir;

   @Test
   @DisplayName("AuthPathTool deve retornar o caminho raiz terminado com separador")
   void authPathToolShouldReturnRootPath() {
      AuthPathTool tool = new AuthPathTool(tempDir);
      CallToolRequest request = CallToolRequest.builder(McpConsts.APT_TOOL_NAME)
                                               .arguments(Map.of())
                                               .build();

      CallToolResult result = tool.run(null, request);

      assertFalse(result.isError());
      TextContent content = assertInstanceOf(TextContent.class,
                                             result.content().get(0));
      assertTrue(content.text().startsWith(tempDir.toString()));
      assertTrue(content.text().endsWith(File.separator));
   }

   @Test
   @DisplayName("AuthPathTool deve rejeitar raiz nula no construtor")
   void authPathToolShouldRejectNullRoot() {
      assertThrows(IllegalArgumentException.class, () -> new AuthPathTool(null));
   }

   @Test
   @DisplayName("AboutResource deve retornar nome, versão e descrição do servidor")
   void aboutResourceShouldReturnAppMetadata() {
      AboutResource resource = new AboutResource();
      ReadResourceRequest request =
         ReadResourceRequest.builder(McpConsts.RESOURCE_ABOUT_URI).build();

      ReadResourceResult result = resource.read(null, request);

      TextResourceContents text = assertInstanceOf(
         TextResourceContents.class, result.contents().get(0));
      assertTrue(text.text().contains(McpConsts.APP_NAME));
      assertTrue(text.text().contains(McpConsts.APP_VERSION));
   }

   @Test
   @DisplayName("RootInfoResource deve informar estado do diretório autorizado")
   void rootInfoResourceShouldReturnDirectoryStatus() {
      RootInfoResource resource = new RootInfoResource(tempDir);
      ReadResourceRequest request =
         ReadResourceRequest.builder(McpConsts.RESOURCE_ROOT_INFO_URI).build();

      ReadResourceResult result = resource.read(null, request);

      TextResourceContents text = assertInstanceOf(
         TextResourceContents.class, result.contents().get(0));
      assertTrue(text.text().contains(tempDir.toString()));
      assertTrue(text.text().contains("É diretório Sim"));
      assertTrue(text.text().contains("Habilitado para leitura Sim"));
   }

   @Test
   @DisplayName("ServerComposition deve compor tools e resources para um diretório válido")
   void serverCompositionShouldWireDependencies() {
      ServerComposition composition = new ServerComposition(tempDir);

      assertFalse(composition.getTools().isEmpty());
      assertFalse(composition.getResources().isEmpty());
   }
}
