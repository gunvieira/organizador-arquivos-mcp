package br.edu.ifpr.mcp_organizer.services.scan;

import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import br.edu.ifpr.mcp_organizer.services.classification.ClassificationRules;
import br.edu.ifpr.mcp_organizer.services.classification.ExtensionClassifier;
import br.edu.ifpr.mcp_organizer.services.classification.FileClassifier;
import br.edu.ifpr.mcp_organizer.services.security.SandboxPathResolver;

/**
 * Serviço responsável por varrer diretórios dentro da sandbox respeitando os
 * limites de entradas ({@code 20.000}), profundidade ({@code 32}), ignorando
 * links simbólicos ({@code NOFOLLOW_LINKS}) e a pasta interna
 * {@code .mcp-organizer}.
 */
public class FileScanner {

   public static final int MAX_ENTRIES_SCANNED = 20_000;
   public static final int MAX_DEPTH           = 32;
   public static final int SAMPLE_LIMIT        = 15;

   private final SandboxPathResolver pathResolver;
   private final FileClassifier classifier;
   private final ClassificationRules rules;
   private final int maxEntriesScanned;

   public FileScanner(SandboxPathResolver pathResolver,
                      FileClassifier classifier,
                      ClassificationRules rules) {
      this(pathResolver, classifier, rules, MAX_ENTRIES_SCANNED);
   }

   public FileScanner(SandboxPathResolver pathResolver,
                      FileClassifier classifier,
                      ClassificationRules rules,
                      int maxEntriesScanned) {
      if (pathResolver == null)
         throw new IllegalArgumentException(
            "O resolvedor de caminhos não pode ser nulo.");
      if (classifier == null)
         throw new IllegalArgumentException(
            "O classificador de arquivos não pode ser nulo.");
      if (rules == null)
         throw new IllegalArgumentException(
            "As regras de classificação não podem ser nulas.");
      if (maxEntriesScanned < 1)
         throw new IllegalArgumentException(
            "O limite de entradas varridas deve ser positivo.");

      this.pathResolver      = pathResolver;
      this.classifier        = classifier;
      this.rules             = rules;
      this.maxEntriesScanned = maxEntriesScanned;
   }

   /**
    * Resultado bruto de uma varredura de arquivos.
    */
   public record ScanResult(
      Path targetDirectory,
      String relativeTargetDirectory,
      boolean recursive,
      List<FileInfo> files,
      boolean scanLimitReached,
      boolean maxDepthReached
   ) {}

   /**
    * Varre um subdiretório (ou a raiz se {@code relativePath} for nulo/vazio)
    * coletando metadados dos arquivos regulares encontrados.
    */
   public ScanResult scan(String relativePath, boolean recursive) {
      Path targetDir = pathResolver.resolveExistingDirectory(relativePath);
      String relTarget = pathResolver.relativize(targetDir);
      int allowedDepth = recursive ? MAX_DEPTH : 1;

      List<FileInfo> files = new ArrayList<>();
      boolean[] limitReached = { false };
      boolean[] depthReached = { false };
      int[] entriesVisited   = { 0 };

      try {
         Files.walkFileTree(
            targetDir,
            EnumSet.noneOf(FileVisitOption.class),
            allowedDepth + 1,
            new SimpleFileVisitor<>() {

               @Override
               public FileVisitResult preVisitDirectory(Path dir,
                                                        BasicFileAttributes attrs) {
                  if (dir.equals(targetDir))
                     return FileVisitResult.CONTINUE;

                  if (pathResolver.isAppDataPath(dir) ||
                      Files.isSymbolicLink(dir) ||
                      attrs.isSymbolicLink())
                     return FileVisitResult.SKIP_SUBTREE;

                  int currentDepth = targetDir.relativize(dir).getNameCount();
                  if (!recursive && currentDepth >= 1)
                     return FileVisitResult.SKIP_SUBTREE;

                  if (recursive && currentDepth > MAX_DEPTH) {
                     depthReached[0] = true;
                     return FileVisitResult.SKIP_SUBTREE;
                  }

                  entriesVisited[0]++;
                  if (entriesVisited[0] > maxEntriesScanned) {
                     limitReached[0] = true;
                     return FileVisitResult.TERMINATE;
                  }

                  return FileVisitResult.CONTINUE;
               }

               @Override
               public FileVisitResult visitFile(Path file,
                                                BasicFileAttributes attrs) {
                  if (pathResolver.isAppDataPath(file) ||
                      Files.isSymbolicLink(file) ||
                      attrs.isSymbolicLink())
                     return FileVisitResult.CONTINUE;

                  int fileDepth = targetDir.relativize(file).getNameCount();
                  if (recursive && fileDepth > MAX_DEPTH) {
                     depthReached[0] = true;
                     return FileVisitResult.CONTINUE;
                  }

                  entriesVisited[0]++;
                  if (entriesVisited[0] > maxEntriesScanned) {
                     limitReached[0] = true;
                     return FileVisitResult.TERMINATE;
                  }

                  if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS))
                     return FileVisitResult.CONTINUE;

                  String fileName = file.getFileName().toString();
                  String ext      = ExtensionClassifier.extractExtension(fileName);
                  String category = classifier.classify(file);
                  String relPath  = pathResolver.relativize(file);

                  files.add(new FileInfo(
                     file,
                     relPath,
                     fileName,
                     ext,
                     attrs.size(),
                     attrs.lastModifiedTime().toInstant(),
                     category
                  ));
                  return FileVisitResult.CONTINUE;
               }

               @Override
               public FileVisitResult visitFileFailed(Path file, IOException exc) {
                  return FileVisitResult.CONTINUE;
               }
            }
         );
      }
      catch (IOException ex) {
         throw new IllegalStateException(
            "Erro ao varrer diretório '" + relTarget + "': " + ex.getMessage(),
            ex);
      }

      return new ScanResult(
         targetDir,
         relTarget,
         recursive,
         files,
         limitReached[0],
         depthReached[0]
      );
   }

   /**
    * Gera o relatório textual completo para a tool {@code escanear_pasta} e
    * para o resource {@code app://estatisticas}.
    */
   public String formatScanReport(String relativePath, boolean recursive) {
      ScanResult result = scan(relativePath, recursive);
      List<FileInfo> files = result.files();

      long totalBytes = 0L;
      Map<String, Integer> countByCat = new LinkedHashMap<>();
      Map<String, Long> sizeByCat     = new LinkedHashMap<>();

      for (String cat : rules.getCategoryNames()) {
         countByCat.put(cat, 0);
         sizeByCat.put(cat, 0L);
      }

      for (FileInfo f : files) {
         totalBytes += f.size();
         countByCat.merge(f.category(), 1, Integer::sum);
         sizeByCat.merge(f.category(), f.size(), Long::sum);
      }

      StringBuilder sb = new StringBuilder();
      sb.append("Diretório escaneado: ").append(result.relativeTargetDirectory()).append('\n')
        .append("Busca recursiva: ").append(recursive ? "sim" : "não").append('\n')
        .append("Total de arquivos: ").append(files.size()).append('\n')
        .append("Espaço total ocupado: ").append(FileInfo.formatBytes(totalBytes))
        .append(" (").append(totalBytes).append(" bytes)\n\n")
        .append("Distribuição por categoria:\n");

      for (Map.Entry<String, Integer> entry : countByCat.entrySet()) {
         String cat = entry.getKey();
         int count  = entry.getValue();
         long bytes = sizeByCat.getOrDefault(cat, 0L);
         sb.append("- ").append(cat).append(": ")
           .append(count).append(" arquivo(s) | ")
           .append(FileInfo.formatBytes(bytes))
           .append(" (").append(bytes).append(" bytes)\n");
      }

      if (files.isEmpty()) {
         sb.append("\nNenhum arquivo encontrado no diretório.\n");
      }
      else {
         int sampleCount = Math.min(files.size(), SAMPLE_LIMIT);
         sb.append("\nAmostra de arquivos (").append(sampleCount)
           .append(" de ").append(files.size()).append("):\n");
         for (int i = 0; i < sampleCount; i++)
            sb.append("- ").append(files.get(i).toSummaryLine()).append('\n');
      }

      if (result.scanLimitReached())
         sb.append("\nAVISO: Limite de ")
           .append(maxEntriesScanned)
           .append(" entradas examinadas atingido; o resultado pode estar incompleto.\n");

      if (result.maxDepthReached())
         sb.append("\nAVISO: Limite de profundidade máxima (")
           .append(MAX_DEPTH)
           .append(" níveis) atingido; subpastas mais profundas foram ignoradas.\n");

      return sb.toString();
   }
}
