package br.edu.ifpr.mcp_organizer.services.classification;

import java.nio.file.Path;
import java.util.Locale;

/**
 * Implementação de {@link FileClassifier} baseada na extensão do arquivo,
 * sem diferenciar maiúsculas de minúsculas.
 */
public class ExtensionClassifier implements FileClassifier {

   private final ClassificationRules rules;

   public ExtensionClassifier(ClassificationRules rules) {
      if (rules == null)
         throw new IllegalArgumentException(
            "As regras de classificação não podem ser nulas.");
      this.rules = rules;
   }

   @Override
   public String classify(Path file) {
      if (file == null || file.getFileName() == null)
         return rules.getDefaultCategory();
      return classifyByName(file.getFileName().toString());
   }

   @Override
   public String classifyByName(String fileName) {
      String ext = extractExtension(fileName);
      return rules.categoryForExtension(ext);
   }

   /**
    * Extrai a extensão de um nome de arquivo em minúsculas (sem o ponto),
    * ou string vazia se não possuir extensão.
    */
   public static String extractExtension(String fileName) {
      if (fileName == null || fileName.isBlank())
         return "";
      String trimmed = fileName.trim();
      int lastDot = trimmed.lastIndexOf('.');
      // Se não tem ponto, começa apenas com ponto (.gitignore) ou termina com ponto
      if (lastDot <= 0 || lastDot == trimmed.length() - 1)
         return "";
      return trimmed.substring(lastDot + 1).toLowerCase(Locale.ROOT);
   }

   public ClassificationRules getRules() {
      return rules;
   }
}
