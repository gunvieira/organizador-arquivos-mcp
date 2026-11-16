package br.edu.ifpr.mcp_organizer.services.duplicates;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import br.edu.ifpr.mcp_organizer.services.scan.FileInfo;
import br.edu.ifpr.mcp_organizer.services.scan.FileScanner;
import br.edu.ifpr.mcp_organizer.services.scan.FileScanner.ScanResult;

/**
 * Serviço para detecção de arquivos duplicados (somente leitura) utilizando um
 * pipeline em 3 etapas:
 * <ol>
 *   <li>Agrupamento por tamanho em bytes (ignorando arquivos vazios de 0 bytes);</li>
 *   <li>Agrupamento por hash parcial dos primeiros 64 KB (evita leitura completa desnecessária);</li>
 *   <li>Agrupamento por hash completo SHA-256.</li>
 * </ol>
 */
public class DuplicateFinderService {

   private static final Logger LOG =
      LoggerFactory.getLogger(DuplicateFinderService.class);

   /** Tamanho do bloco de hash parcial: 64 KB. */
   public static final int PARTIAL_HASH_BYTES = 64 * 1024;

   private final FileScanner scanner;
   private final HashStrategy hashStrategy;

   public DuplicateFinderService(FileScanner scanner,
                                 HashStrategy hashStrategy) {
      if (scanner == null)
         throw new IllegalArgumentException(
            "O scanner de arquivos não pode ser nulo.");
      if (hashStrategy == null)
         throw new IllegalArgumentException(
            "A estratégia de hash não pode ser nula.");
      this.scanner      = scanner;
      this.hashStrategy = hashStrategy;
   }

   /**
    * Representa um grupo de arquivos duplicados com mesmo tamanho e mesmo SHA-256.
    */
   public record DuplicateGroup(
      String sha256,
      long fileSize,
      List<FileInfo> files
   ) {
      /**
       * Espaço desperdiçado pelo grupo: {@code (quantidade - 1) * tamanho}.
       */
      public long wastedBytes() {
         return files.size() > 1 ? (files.size() - 1L) * fileSize : 0L;
      }
   }

   /**
    * Resultado consolidado da busca por duplicados.
    */
   public record DuplicateReportData(
      ScanResult scanResult,
      long effectiveMinSize,
      List<DuplicateGroup> groups,
      long totalWastedBytes
   ) {}

   /**
    * Executa o pipeline de 3 etapas e retorna os grupos de duplicados encontrados.
    */
   public DuplicateReportData findDuplicates(String relativePath,
                                             boolean recursive,
                                             long minSize) {
      if (minSize < 0)
         throw new IllegalArgumentException(
            "'min_size' não pode ser negativo (informado: " + minSize + ").");

      // Regra de negócio: ignorar sempre arquivos de 0 bytes
      long effectiveMinSize = Math.max(1L, minSize);
      ScanResult scanResult = scanner.scan(relativePath, recursive);

      // Etapa 1: Agrupar por tamanho
      Map<Long, List<FileInfo>> bySize = new LinkedHashMap<>();
      for (FileInfo f : scanResult.files()) {
         if (f.size() >= effectiveMinSize)
            bySize.computeIfAbsent(f.size(), k -> new ArrayList<>()).add(f);
      }

      List<DuplicateGroup> duplicateGroups = new ArrayList<>();

      for (Map.Entry<Long, List<FileInfo>> sizeEntry : bySize.entrySet()) {
         List<FileInfo> sameSizeFiles = sizeEntry.getValue();
         if (sameSizeFiles.size() < 2)
            continue;

         long fileSize = sizeEntry.getKey();

         // Etapa 2: Agrupar por hash parcial (primeiros 64 KB)
         Map<String, List<FileInfo>> byPartialHash = new LinkedHashMap<>();
         for (FileInfo f : sameSizeFiles) {
            try {
               String partial = hashStrategy.computePartialHash(
                  f.absolutePath(), PARTIAL_HASH_BYTES);
               byPartialHash.computeIfAbsent(partial, k -> new ArrayList<>())
                            .add(f);
            }
            catch (IOException ex) {
               LOG.warn("Não foi possível ler hash parcial de '{}': {}",
                        f.relativePath(), ex.getMessage());
            }
         }

         // Etapa 3: Para grupos com >= 2 arquivos de mesmo hash parcial, calcular hash completo
         for (Map.Entry<String, List<FileInfo>> partialEntry : byPartialHash.entrySet()) {
            List<FileInfo> samePartialFiles = partialEntry.getValue();
            if (samePartialFiles.size() < 2)
               continue;

            // Se o arquivo cabe inteiramente nos 64 KB iniciais, o hash parcial já é o hash completo
            if (fileSize <= PARTIAL_HASH_BYTES) {
               List<FileInfo> sorted = samePartialFiles.stream()
                  .sorted(Comparator.comparing(FileInfo::relativePath))
                  .toList();
               duplicateGroups.add(new DuplicateGroup(
                  partialEntry.getKey(), fileSize, sorted));
               continue;
            }

            Map<String, List<FileInfo>> byFullHash = new LinkedHashMap<>();
            for (FileInfo f : samePartialFiles) {
               try {
                  String full = hashStrategy.computeFullHash(f.absolutePath());
                  byFullHash.computeIfAbsent(full, k -> new ArrayList<>())
                            .add(f);
               }
               catch (IOException ex) {
                  LOG.warn("Não foi possível ler hash completo de '{}': {}",
                           f.relativePath(), ex.getMessage());
               }
            }

            for (Map.Entry<String, List<FileInfo>> fullEntry : byFullHash.entrySet()) {
               if (fullEntry.getValue().size() >= 2) {
                  List<FileInfo> sorted = fullEntry.getValue().stream()
                     .sorted(Comparator.comparing(FileInfo::relativePath))
                     .toList();
                  duplicateGroups.add(new DuplicateGroup(
                     fullEntry.getKey(), fileSize, sorted));
               }
            }
         }
      }

      // Ordena os grupos pelo maior desperdício de espaço (decrescente)
      duplicateGroups.sort(
         Comparator.comparingLong(DuplicateGroup::wastedBytes).reversed());

      long totalWasted = duplicateGroups.stream()
         .mapToLong(DuplicateGroup::wastedBytes)
         .sum();

      return new DuplicateReportData(
         scanResult, effectiveMinSize, duplicateGroups, totalWasted);
   }

   /**
    * Gera o relatório textual para a tool {@code encontrar_duplicados}.
    */
   public String formatDuplicatesReport(String relativePath,
                                        boolean recursive,
                                        long minSize) {
      DuplicateReportData data = findDuplicates(relativePath, recursive, minSize);
      List<DuplicateGroup> groups = data.groups();

      StringBuilder sb = new StringBuilder();
      sb.append("Diretório analisado: ")
        .append(data.scanResult().relativeTargetDirectory()).append('\n')
        .append("Busca recursiva: ").append(recursive ? "sim" : "não").append('\n')
        .append("Tamanho mínimo considerado: ")
        .append(FileInfo.formatBytes(data.effectiveMinSize()))
        .append(" (arquivos de 0 bytes são ignorados)\n")
        .append("Grupos de duplicados encontrados: ").append(groups.size()).append('\n')
        .append("Espaço total desperdiçado: ")
        .append(FileInfo.formatBytes(data.totalWastedBytes()))
        .append(" (").append(data.totalWastedBytes()).append(" bytes)\n");

      if (groups.isEmpty()) {
         sb.append("\nNenhum arquivo duplicado encontrado.\n");
      }
      else {
         for (int i = 0; i < groups.size(); i++) {
            DuplicateGroup g = groups.get(i);
            sb.append("\nGrupo ").append(i + 1)
              .append(" [").append(g.files().size()).append(" cópias | ")
              .append(FileInfo.formatBytes(g.fileSize()))
              .append(" cada | desperdiçado: ")
              .append(FileInfo.formatBytes(g.wastedBytes()))
              .append(" | SHA-256: ").append(g.sha256(), 0, 16).append("...]:\n");
            for (FileInfo f : g.files())
               sb.append("  - ").append(f.relativePath()).append('\n');
         }
      }

      if (data.scanResult().scanLimitReached())
         sb.append("\nAVISO: Limite de entradas examinadas atingido; a análise pode estar incompleta.\n");

      return sb.toString();
   }
}
