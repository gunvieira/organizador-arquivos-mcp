package br.edu.ifpr.mcp_organizer.mcp.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;

class ToolResultsTest {

   @Test
   @DisplayName("Deve construir resultado de sucesso com mensagem informada")
   void shouldBuildSuccessResult() {
      CallToolResult result = ToolResults.success("Operação concluída");

      assertFalse(result.isError());
      assertEquals(1, result.content().size());
      TextContent text = assertInstanceOf(TextContent.class,
                                          result.content().get(0));
      assertEquals("Operação concluída", text.text());
   }

   @Test
   @DisplayName("Deve construir resultado de erro com mensagem informada")
   void shouldBuildErrorResult() {
      CallToolResult result = ToolResults.error("Falha na validação");

      assertTrue(result.isError());
      assertEquals(1, result.content().size());
      TextContent text = assertInstanceOf(TextContent.class,
                                          result.content().get(0));
      assertEquals("Falha na validação", text.text());
   }

   @ParameterizedTest(name = "Deve usar mensagem padrão quando entrada for \"{0}\"")
   @NullAndEmptySource
   @ValueSource(strings = { "   " })
   void shouldFallbackToDefaultMessageWhenBlankOrNull(String input) {
      CallToolResult result = ToolResults.error(input);

      assertTrue(result.isError());
      TextContent text = assertInstanceOf(TextContent.class,
                                          result.content().get(0));
      assertFalse(text.text().isBlank());
   }
}
