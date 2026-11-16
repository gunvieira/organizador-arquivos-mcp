package br.edu.ifpr.mcp_organizer.services.classification;

import java.io.InputStream;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * Representa as regras de classificação por extensão carregadas do arquivo
 * {@code /classification-rules.json}.
 */
public final class ClassificationRules {

   public static final String DEFAULT_RESOURCE_PATH =
                                                  "/classification-rules.json";
   public static final String FALLBACK_CATEGORY     = "Outros";

   private static final ObjectMapper MAPPER = new ObjectMapper();

   private final String defaultCategory;
   private final Map<String, List<String>> categoriesToExtensions;
   private final Map<String, String> extensionToCategory;

   public ClassificationRules(String defaultCategory,
                              Map<String, List<String>> categories) {
      if (categories == null || categories.isEmpty())
         throw new IllegalArgumentException(
            "O mapa de categorias não pode ser nulo ou vazio.");

      this.defaultCategory = (defaultCategory == null || defaultCategory.isBlank())
                             ? FALLBACK_CATEGORY
                             : defaultCategory.trim();

      Map<String, List<String>> catMap = new LinkedHashMap<>();
      Map<String, String> extMap       = new LinkedHashMap<>();

      for (Map.Entry<String, List<String>> entry : categories.entrySet()) {
         String category = entry.getKey() != null ? entry.getKey().trim() : "";
         if (category.isEmpty())
            continue;

         List<String> rawExts = entry.getValue() != null
                                ? entry.getValue()
                                : List.of();
         List<String> normalizedExts = rawExts.stream()
                                              .filter(e -> e != null && !e.isBlank())
                                              .map(ClassificationRules::normalizeExt)
                                              .toList();
         catMap.put(category, normalizedExts);
         for (String ext : normalizedExts)
            extMap.put(ext, category);
      }

      if (!catMap.containsKey(this.defaultCategory))
         catMap.put(this.defaultCategory, List.of());

      this.categoriesToExtensions = Collections.unmodifiableMap(catMap);
      this.extensionToCategory    = Collections.unmodifiableMap(extMap);
   }

   /**
    * Carrega as regras padrão do arquivo {@code /classification-rules.json}
    * no classpath.
    */
   public static ClassificationRules loadDefault() {
      return loadFromResource(DEFAULT_RESOURCE_PATH);
   }

   /**
    * Carrega as regras de um caminho de recurso no classpath.
    */
   @SuppressWarnings("unchecked")
   public static ClassificationRules loadFromResource(String resourcePath) {
      if (resourcePath == null || resourcePath.isBlank())
         throw new IllegalArgumentException(
            "O caminho do recurso de regras não pode ser vazio.");

      String normalized = resourcePath.startsWith("/")
                          ? resourcePath
                          : "/" + resourcePath;

      try (InputStream is = ClassificationRules.class
                                               .getResourceAsStream(normalized)) {
         if (is == null)
            throw new IllegalArgumentException(
               "Arquivo de regras de classificação não encontrado: " +
               resourcePath);

         Map<String, Object> raw = MAPPER.readValue(
            is, new TypeReference<Map<String, Object>>() {});

         String defaultCat = (String) raw.getOrDefault("defaultCategory",
                                                       FALLBACK_CATEGORY);
         Object catsObj = raw.get("categories");
         if (!(catsObj instanceof Map<?, ?> rawCats))
            throw new IllegalArgumentException(
               "Estrutura inválida no arquivo de regras: 'categories' ausente.");

         Map<String, List<String>> typedCats = new LinkedHashMap<>();
         for (Map.Entry<?, ?> entry : rawCats.entrySet()) {
            String key = String.valueOf(entry.getKey());
            Object val = entry.getValue();
            if (val instanceof List<?> list) {
               List<String> strList = list.stream()
                                          .map(String::valueOf)
                                          .toList();
               typedCats.put(key, strList);
            }
         }
         return new ClassificationRules(defaultCat, typedCats);
      }
      catch (IllegalArgumentException ex) {
         throw ex;
      }
      catch (Exception ex) {
         throw new IllegalStateException(
            "Falha ao carregar regras de classificação: " + ex.getMessage(), ex);
      }
   }

   /**
    * Obtém a categoria associada a uma extensão, ou a categoria padrão ("Outros").
    */
   public String categoryForExtension(String extension) {
      if (extension == null || extension.isBlank())
         return defaultCategory;
      String normalized = normalizeExt(extension);
      return extensionToCategory.getOrDefault(normalized, defaultCategory);
   }

   /**
    * Verifica se o nome informado corresponde a uma categoria válida (ignorando
    * maiúsculas/minúsculas) e retorna o nome canônico da categoria, ou {@code null}
    * se não existir.
    */
   public String resolveCanonicalCategory(String categoryName) {
      if (categoryName == null || categoryName.isBlank())
         return null;
      String trimmed = categoryName.trim();
      for (String cat : categoriesToExtensions.keySet()) {
         if (cat.equalsIgnoreCase(trimmed))
            return cat;
      }
      return null;
   }

   public String getDefaultCategory() {
      return defaultCategory;
   }

   public Map<String, List<String>> getCategoriesToExtensions() {
      return categoriesToExtensions;
   }

   public Set<String> getCategoryNames() {
      return categoriesToExtensions.keySet();
   }

   public static String normalizeExt(String ext) {
      if (ext == null)
         return "";
      String s = ext.trim().toLowerCase(Locale.ROOT);
      while (s.startsWith("."))
         s = s.substring(1);
      return s;
   }
}
