package br.edu.ifpr.mcp_organizer.services.scan;

import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Metadados imutáveis de um arquivo varrido na sandbox.
 *
 * @param absolutePath caminho absoluto real dentro da raiz
 * @param relativePath caminho relativo à raiz (usando '/')
 * @param fileName     nome do arquivo
 * @param extension    extensão em minúsculas sem ponto (ou vazio)
 * @param size         tamanho em bytes
 * @param lastModified instante da última modificação
 * @param category     categoria atribuída pelo classificador
 */
public record FileInfo(
   Path absolutePath,
   String relativePath,
   String fileName,
   String extension,
   long size,
   Instant lastModified,
   String category
) {
   private static final DateTimeFormatter DATE_FMT =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneOffset.UTC);

   /**
    * Formata um resumo legível em uma linha:
    * {@code caminho | tamanho | modificado | categoria}.
    */
   public String toSummaryLine() {
      return "%s | %s (%d bytes) | %s | %s".formatted(
         relativePath,
         formatBytes(size),
         size,
         DATE_FMT.format(lastModified),
         category
      );
   }

   /**
    * Formata bytes em representação humana (B, KB, MB, GB).
    */
   public static String formatBytes(long bytes) {
      if (bytes < 1024)
         return bytes + " B";
      double kb = bytes / 1024.0;
      if (kb < 1024)
         return String.format(Locale.ROOT, "%.2f KB", kb);
      double mb = kb / 1024.0;
      if (mb < 1024)
         return String.format(Locale.ROOT, "%.2f MB", mb);
      double gb = mb / 1024.0;
      return String.format(Locale.ROOT, "%.2f GB", gb);
   }
}
