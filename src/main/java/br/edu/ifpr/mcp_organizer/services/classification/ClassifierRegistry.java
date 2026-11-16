package br.edu.ifpr.mcp_organizer.services.classification;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Fábrica e registro (padrão Factory / Registry) de estratégias de
 * classificação de arquivos ({@link FileClassifier}).
 */
public final class ClassifierRegistry {

   public static final String EXTENSION_STRATEGY = "extension";

   private final Map<String, FileClassifier> registry = new LinkedHashMap<>();
   private final ClassificationRules defaultRules;

   public ClassifierRegistry() {
      this(ClassificationRules.loadDefault());
   }

   public ClassifierRegistry(ClassificationRules rules) {
      if (rules == null)
         throw new IllegalArgumentException(
            "As regras de classificação não podem ser nulas.");
      this.defaultRules = rules;
      register(EXTENSION_STRATEGY, new ExtensionClassifier(rules));
   }

   /**
    * Registra uma nova estratégia de classificação.
    */
   public void register(String name, FileClassifier classifier) {
      if (name == null || name.isBlank())
         throw new IllegalArgumentException(
            "O nome da estratégia de classificação não pode ser vazio.");
      if (classifier == null)
         throw new IllegalArgumentException(
            "O classificador não pode ser nulo.");
      registry.put(name.trim().toLowerCase(Locale.ROOT), classifier);
   }

   /**
    * Obtém um classificador pelo nome da estratégia.
    */
   public FileClassifier getClassifier(String name) {
      if (name == null || name.isBlank())
         throw new IllegalArgumentException(
            "Informe o nome da estratégia de classificação.");
      FileClassifier found = registry.get(name.trim().toLowerCase(Locale.ROOT));
      if (found == null)
         throw new IllegalArgumentException(
            "Estratégia de classificação não encontrada: " + name);
      return found;
   }

   /**
    * Retorna o classificador padrão por extensão.
    */
   public FileClassifier getDefaultClassifier() {
      return getClassifier(EXTENSION_STRATEGY);
   }

   public ClassificationRules getDefaultRules() {
      return defaultRules;
   }

   public Map<String, FileClassifier> getRegisteredClassifiers() {
      return Collections.unmodifiableMap(registry);
   }
}
