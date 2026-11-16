package br.edu.ifpr.mcp_organizer.services.search;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import br.edu.ifpr.mcp_organizer.services.classification.ClassificationRules;
import br.edu.ifpr.mcp_organizer.services.scan.FileInfo;
import br.edu.ifpr.mcp_organizer.services.scan.FileScanner;
import br.edu.ifpr.mcp_organizer.services.scan.FileScanner.ScanResult;

/**
 * Serviço de busca e listagem ordenada de arquivos (busca multicritério,
 * listagem dos maiores arquivos e listagem dos arquivos mais antigos).
 */
public class FileSearchService {

   public static final int DEFAULT_RANK_LIMIT = 10;
   public static final int MAX_RANK_LIMIT     = 100;

   private final FileScanner scanner;
   private final ClassificationRules rules;

   public FileSearchService(FileScanner scanner, ClassificationRules rules) {
      if (scanner == null)
         throw new IllegalArgumentException(
            "O scanner de arquivos não pode ser nulo.");
      if (rules == null)
         throw new IllegalArgumentException(
            "As regras de classificação não podem ser nulas.");
      this.scanner = scanner;
      this.rules   = rules;
   }

   /**
    * Faz o parse de uma data ISO {@code YYYY-MM-DD} lançando
    * {@link IllegalArgumentException} com mensagem clara em português se inválida.
    */
   public static LocalDate parseIsoDate(String raw, String fieldName) {
      if (raw == null || raw.isBlank())
         return null;
      try {
         return LocalDate.parse(raw.trim());
      }
      catch (DateTimeParseException ex) {
         throw new IllegalArgumentException(
            "Data inválida para '" + fieldName +
            "' (use o formato ISO YYYY-MM-DD): " + raw, ex);
      }
   }

   /**
    * Valida os critérios de busca e retorna o relatório textual formatado.
    */
   public String search(FileCriteria criteria) {
      if (criteria == null)
         throw new IllegalArgumentException(
            "Os critérios de busca não podem ser nulos.");

      validateCriteria(criteria);

      String canonicalCat = criteria.getCategory() != null
         ? rules.resolveCanonicalCategory(criteria.getCategory())
         : null;

      FileCriteria effectiveCriteria = FileCriteria.builder()
         .nameGlob(criteria.getNameGlob())
         .category(canonicalCat)
         .extensions(new ArrayList<>(criteria.getExtensions()))
         .minSize(criteria.getMinSize())
         .maxSize(criteria.getMaxSize())
         .modifiedAfter(criteria.getModifiedAfter())
         .modifiedBefore(criteria.getModifiedBefore())
         .recursive(criteria.isRecursive())
         .maxResults(criteria.getMaxResults())
         .build();

      FileSpecification spec = FileSpecification.fromCriteria(effectiveCriteria);
      ScanResult scanResult  = scanner.scan(null, effectiveCriteria.isRecursive());

      List<FileInfo> matched = new ArrayList<>();
      boolean resultLimitReached = false;

      for (FileInfo info : scanResult.files()) {
         if (spec.test(info)) {
            if (matched.size() == effectiveCriteria.getMaxResults()) {
               resultLimitReached = true;
               break;
            }
            matched.add(info);
         }
      }

      StringBuilder sb = new StringBuilder();
      sb.append("Busca recursiva: ")
        .append(effectiveCriteria.isRecursive() ? "sim" : "não").append('\n')
        .append("Resultados encontrados: ").append(matched.size()).append('\n');

      if (matched.isEmpty()) {
         sb.append("\nNenhum arquivo encontrado para os filtros informados.\n");
      }
      else {
         sb.append("\nArquivos (caminho | tamanho | modificado | categoria):\n");
         for (FileInfo info : matched)
            sb.append("- ").append(info.toSummaryLine()).append('\n');
      }

      if (resultLimitReached)
         sb.append("\nAVISO: Limite de resultados (")
           .append(effectiveCriteria.getMaxResults())
           .append(") atingido; podem existir mais ocorrências.\n");

      if (scanResult.scanLimitReached())
         sb.append("\nAVISO: Limite de entradas examinadas atingido; a busca pode estar incompleta.\n");

      return sb.toString();
   }

   /**
    * Lista os maiores arquivos em ordem decrescente de tamanho.
    */
   public String listLargest(String relativePath, int limit, boolean recursive) {
      validateLimit(limit, "limit");
      ScanResult scanResult = scanner.scan(relativePath, recursive);

      List<FileInfo> sorted = scanResult.files().stream()
         .sorted(Comparator.comparingLong(FileInfo::size).reversed()
                           .thenComparing(FileInfo::relativePath))
         .limit(limit)
         .toList();

      return formatRankedList(
         "Maiores arquivos",
         scanResult,
         sorted,
         limit
      );
   }

   /**
    * Lista os arquivos mais antigos em ordem crescente de data de modificação.
    */
   public String listOldest(String relativePath, int limit, boolean recursive) {
      validateLimit(limit, "limit");
      ScanResult scanResult = scanner.scan(relativePath, recursive);

      List<FileInfo> sorted = scanResult.files().stream()
         .sorted(Comparator.comparing(FileInfo::lastModified)
                           .thenComparing(FileInfo::relativePath))
         .limit(limit)
         .toList();

      return formatRankedList(
         "Arquivos mais antigos",
         scanResult,
         sorted,
         limit
      );
   }

   private void validateCriteria(FileCriteria c) {
      validateLimit(c.getMaxResults(), "max_results");

      if (c.getCategory() != null &&
          rules.resolveCanonicalCategory(c.getCategory()) == null)
         throw new IllegalArgumentException(
            "Categoria inexistente: '" + c.getCategory() +
            "'. Categorias válidas: " +
            String.join(", ", rules.getCategoryNames()));

      if (c.getMinSize() != null && c.getMinSize() < 0)
         throw new IllegalArgumentException(
            "'min_size' não pode ser negativo.");

      if (c.getMaxSize() != null && c.getMaxSize() < 0)
         throw new IllegalArgumentException(
            "'max_size' não pode ser negativo.");

      if (c.getMinSize() != null && c.getMaxSize() != null &&
          c.getMinSize() > c.getMaxSize())
         throw new IllegalArgumentException(
            "Filtro inválido: 'min_size' (" + c.getMinSize() +
            ") não pode ser maior que 'max_size' (" + c.getMaxSize() + ").");

      if (c.getModifiedAfter() != null && c.getModifiedBefore() != null &&
          c.getModifiedAfter().isAfter(c.getModifiedBefore()))
         throw new IllegalArgumentException(
            "Filtro inválido: 'modified_after' (" + c.getModifiedAfter() +
            ") não pode ser posterior a 'modified_before' (" +
            c.getModifiedBefore() + ").");
   }

   private static void validateLimit(int limit, String paramName) {
      if (limit < 1 || limit > MAX_RANK_LIMIT)
         throw new IllegalArgumentException(
            "'" + paramName + "' deve estar entre 1 e " + MAX_RANK_LIMIT +
            " (informado: " + limit + ").");
   }

   private static String formatRankedList(String title,
                                          ScanResult scanResult,
                                          List<FileInfo> items,
                                          int limit) {
      StringBuilder sb = new StringBuilder();
      sb.append(title).append(" em '")
        .append(scanResult.relativeTargetDirectory()).append("'\n")
        .append("Busca recursiva: ")
        .append(scanResult.recursive() ? "sim" : "não").append('\n')
        .append("Limite solicitado: ").append(limit).append('\n')
        .append("Total exibido: ").append(items.size()).append('\n');

      if (items.isEmpty()) {
         sb.append("\nNenhum arquivo encontrado.\n");
      }
      else {
         sb.append('\n');
         for (int i = 0; i < items.size(); i++)
            sb.append(i + 1).append(". ")
              .append(items.get(i).toSummaryLine()).append('\n');
      }

      if (scanResult.scanLimitReached())
         sb.append("\nAVISO: Limite de entradas examinadas atingido; a listagem pode estar incompleta.\n");

      return sb.toString();
   }
}
