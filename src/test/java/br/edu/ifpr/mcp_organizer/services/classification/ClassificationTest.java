package br.edu.ifpr.mcp_organizer.services.classification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ClassificationTest {

   @ParameterizedTest(name = "Arquivo '{0}' deve ser classificado como '{1}'")
   @CsvSource({
      "foto.JPG, Imagens",
      "icone.svg, Imagens",
      "banner.PSD, Design",
      "layout.fig, Design",
      "relatorio.pdf, Documentos",
      "notas.MD, Documentos",
      "orcamento.xlsx, Planilhas",
      "dados.csv, Planilhas",
      "aula.pptx, Apresentacoes",
      "filme.mkv, Videos",
      "musica.flac, Audios",
      "backup.tar.gz, Compactados",
      " pacote.7z , Compactados",
      "sem_extensao, Outros",
      ".gitignore, Outros",
      "arquivo.desconhecido, Outros",
      "termina_com_ponto., Outros"
   })
   void shouldClassifyFilesByExtensionCaseInsensitively(String fileName,
                                                        String expectedCategory) {
      ClassifierRegistry registry = new ClassifierRegistry();
      FileClassifier classifier   = registry.getDefaultClassifier();

      assertEquals(expectedCategory, classifier.classifyByName(fileName));
      assertEquals(expectedCategory, classifier.classify(Path.of(fileName)));
   }

   @Test
   @DisplayName("Deve tratar entradas nulas ou vazias como categoria padrão Outros")
   void shouldHandleNullOrBlankInputs() {
      ClassificationRules rules = ClassificationRules.loadDefault();
      ExtensionClassifier classifier = new ExtensionClassifier(rules);

      assertEquals("Outros", classifier.classify(null));
      assertEquals("Outros", classifier.classifyByName(null));
      assertEquals("Outros", classifier.classifyByName("   "));
      assertEquals("Outros", rules.categoryForExtension(null));
      assertEquals("Outros", rules.categoryForExtension(""));
   }

   @Test
   @DisplayName("Deve resolver nome canônico de categoria ignorando maiúsculas/minúsculas")
   void shouldResolveCanonicalCategory() {
      ClassificationRules rules = ClassificationRules.loadDefault();

      assertEquals("Imagens", rules.resolveCanonicalCategory("imagens"));
      assertEquals("Documentos", rules.resolveCanonicalCategory("DOCUMENTOS"));
      assertEquals("Outros", rules.resolveCanonicalCategory("outros"));
      assertNull(rules.resolveCanonicalCategory("CategoriaInexistente"));
      assertNull(rules.resolveCanonicalCategory(""));
      assertNull(rules.resolveCanonicalCategory(null));
   }

   @Test
   @DisplayName("Deve registrar e recuperar estratégias no ClassifierRegistry")
   void shouldRegisterAndRetrieveCustomClassifier() {
      ClassifierRegistry registry = new ClassifierRegistry();
      FileClassifier custom = new FileClassifier() {
         @Override
         public String classify(Path file) { return "Custom"; }
         @Override
         public String classifyByName(String name) { return "Custom"; }
      };

      registry.register("custom", custom);
      assertEquals("Custom", registry.getClassifier("CUSTOM").classifyByName("a.txt"));
      assertTrue(registry.getRegisteredClassifiers().containsKey("custom"));

      assertThrows(IllegalArgumentException.class,
                   () -> registry.getClassifier("inexistente"));
      assertThrows(IllegalArgumentException.class,
                   () -> registry.getClassifier(""));
      assertThrows(IllegalArgumentException.class,
                   () -> registry.register("", custom));
      assertThrows(IllegalArgumentException.class,
                   () -> registry.register("ok", null));
   }

   @Test
   @DisplayName("Deve validar erros ao carregar regras inválidas")
   void shouldValidateInvalidRulesCreationAndResource() {
      assertThrows(IllegalArgumentException.class,
                   () -> new ClassificationRules("Outros", Map.of()));
      assertThrows(IllegalArgumentException.class,
                   () -> ClassificationRules.loadFromResource("/nao-existe.json"));
      assertThrows(IllegalArgumentException.class,
                   () -> ClassificationRules.loadFromResource(""));

      ClassificationRules custom = new ClassificationRules(
         "Padrao", Map.of("Textos", List.of(".TXT")));
      assertNotNull(custom);
      assertEquals("Textos", custom.categoryForExtension("txt"));
      assertEquals("Padrao", custom.categoryForExtension("bin"));
   }
}
