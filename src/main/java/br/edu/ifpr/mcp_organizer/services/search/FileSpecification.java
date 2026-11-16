package br.edu.ifpr.mcp_organizer.services.search;

import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.PatternSyntaxException;

import br.edu.ifpr.mcp_organizer.services.scan.FileInfo;

/**
 * Especificação (padrão Specification / Predicate) componível para filtrar
 * instâncias de {@link FileInfo}.
 */
@FunctionalInterface
public interface FileSpecification extends Predicate<FileInfo> {

   /**
    * Compõe todas as especificações definidas em {@link FileCriteria}.
    */
   static FileSpecification fromCriteria(FileCriteria criteria) {
      FileSpecification spec = info -> true;

      if (criteria.getNameGlob() != null)
         spec = spec.and(byNameGlob(criteria.getNameGlob()));

      if (criteria.getCategory() != null)
         spec = spec.and(byCategory(criteria.getCategory()));

      if (!criteria.getExtensions().isEmpty())
         spec = spec.and(byExtensions(criteria.getExtensions()));

      if (criteria.getMinSize() != null)
         spec = spec.and(byMinSize(criteria.getMinSize()));

      if (criteria.getMaxSize() != null)
         spec = spec.and(byMaxSize(criteria.getMaxSize()));

      if (criteria.getModifiedAfter() != null)
         spec = spec.and(byModifiedAfter(criteria.getModifiedAfter()));

      if (criteria.getModifiedBefore() != null)
         spec = spec.and(byModifiedBefore(criteria.getModifiedBefore()));

      return spec;
   }

   default FileSpecification and(FileSpecification other) {
      return info -> this.test(info) && other.test(info);
   }

   static FileSpecification byNameGlob(String glob) {
      if (glob.contains("/") || glob.contains("\\"))
         throw new IllegalArgumentException(
            "O padrão 'name' deve conter apenas o padrão do nome do arquivo, sem barras de diretório: " +
            glob);

      String normalized = glob.trim().toLowerCase(Locale.ROOT);
      final PathMatcher matcher;
      try {
         matcher = FileSystems.getDefault()
                              .getPathMatcher("glob:" + normalized);
      }
      catch (PatternSyntaxException ex) {
         throw new IllegalArgumentException(
            "Padrão glob inválido: " + glob, ex);
      }

      return info -> matcher.matches(
         Path.of(info.fileName().toLowerCase(Locale.ROOT)));
   }

   static FileSpecification byCategory(String canonicalCategory) {
      return info -> canonicalCategory.equalsIgnoreCase(info.category());
   }

   static FileSpecification byExtensions(Set<String> normalizedExts) {
      return info -> normalizedExts.contains(
         info.extension().toLowerCase(Locale.ROOT));
   }

   static FileSpecification byMinSize(long minSize) {
      return info -> info.size() >= minSize;
   }

   static FileSpecification byMaxSize(long maxSize) {
      return info -> info.size() <= maxSize;
   }

   static FileSpecification byModifiedAfter(LocalDate date) {
      Instant startOfDay = date.atStartOfDay(ZoneOffset.UTC).toInstant();
      return info -> !info.lastModified().isBefore(startOfDay);
   }

   static FileSpecification byModifiedBefore(LocalDate date) {
      Instant endOfDay = date.plusDays(1)
                             .atStartOfDay(ZoneOffset.UTC)
                             .toInstant()
                             .minusMillis(1);
      return info -> !info.lastModified().isAfter(endOfDay);
   }
}
