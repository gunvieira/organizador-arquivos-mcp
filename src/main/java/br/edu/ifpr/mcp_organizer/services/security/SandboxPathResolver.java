package br.edu.ifpr.mcp_organizer.services.security;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;

import br.edu.ifpr.mcp_organizer.main.AppConsts;

/**
 * Guardião de segurança (Sandbox) responsável por resolver e validar todos os
 * caminhos dentro do diretório autorizado, impedindo travessia de diretórios
 * ({@code ../}), caminhos absolutos externos, links simbólicos e acesso à
 * pasta de metadados internos ({@code .mcp-organizer}).
 */
public class SandboxPathResolver {

   /** Nome do diretório reservado para persistência de planos e histórico. */
   public static final String APP_DATA_DIR_NAME = ".mcp-organizer";

   private final Path root;

   /**
    * Cria o resolvedor de caminhos a partir de um diretório raiz existente.
    *
    * @param root diretório raiz autorizado
    * @throws IllegalArgumentException se o caminho for nulo, inexistente ou não for diretório
    */
   public SandboxPathResolver(Path root) {
      if (root == null)
         throw new IllegalArgumentException(
            "O diretório raiz autorizado não pode ser nulo.");

      if (Files.isSymbolicLink(root))
         throw new IllegalArgumentException(
            "O diretório raiz autorizado não pode ser um link simbólico.");

      final Path realRoot;
      try {
         realRoot = root.toRealPath(LinkOption.NOFOLLOW_LINKS);
      }
      catch (IOException ex) {
         throw new IllegalArgumentException(
            "Diretório raiz autorizado inexistente ou inacessível: " + root, ex);
      }

      if (!Files.isDirectory(realRoot, LinkOption.NOFOLLOW_LINKS))
         throw new IllegalArgumentException(
            "O caminho raiz informado não é um diretório: " + root);

      this.root = realRoot;
   }

   /**
    * Cria uma instância a partir da propriedade {@code mcp.files.root} ou da
    * variável de ambiente {@code MCP_FILES_ROOT}.
    *
    * @return instância configurada com a raiz autorizada
    */
   public static SandboxPathResolver fromConfiguration() {
      return fromConfiguration(
         System.getProperty(AppConsts.AUTH_DIR_PROP),
         System.getenv(AppConsts.AUTH_DIR_VAR)
      );
   }

   /**
    * Cria uma instância priorizando a propriedade sobre a variável de ambiente.
    *
    * @param propertyRoot    valor da propriedade da JVM
    * @param environmentRoot valor da variável de ambiente
    * @return instância configurada
    */
   public static SandboxPathResolver fromConfiguration(String propertyRoot,
                                                       String environmentRoot) {
      String configured = propertyRoot;
      if (configured == null || configured.isBlank())
         configured = environmentRoot;

      if (configured == null || configured.isBlank())
         throw new IllegalStateException(
            "Configure a variável de ambiente '" + AppConsts.AUTH_DIR_VAR +
            "' ou a propriedade '" + AppConsts.AUTH_DIR_PROP + "'.");

      try {
         return new SandboxPathResolver(Path.of(configured.trim()));
      }
      catch (InvalidPathException ex) {
         throw new IllegalArgumentException(
            "Caminho configurado para o diretório autorizado é inválido: " +
            configured, ex);
      }
   }

   /**
    * Retorna o caminho real (absoluto e normalizado) da raiz autorizada.
    */
   public Path getRoot() {
      return root;
   }

   /**
    * Retorna o caminho da pasta interna {@code <raiz>/.mcp-organizer}.
    */
   public Path getAppDataDir() {
      return root.resolve(APP_DATA_DIR_NAME);
   }

   /**
    * Resolve um caminho relativo dentro da raiz autorizada, validando que não
    * seja absoluto, não contenha {@code ..}, não atravesse links simbólicos e
    * não aponte para a pasta reservada {@code .mcp-organizer}.
    *
    * @param relativePath caminho relativo (nulo, vazio ou "." retorna a raiz)
    * @return caminho absoluto validado dentro da raiz
    */
   public Path resolve(String relativePath) {
      if (relativePath == null || relativePath.isBlank() ||
          ".".equals(relativePath.trim()))
         return root;

      String trimmed = relativePath.trim();

      // Rejeita caminhos absolutos tanto no formato POSIX quanto Windows (ex.: C:\, \\)
      if (trimmed.startsWith("/") || trimmed.startsWith("\\") ||
          trimmed.matches("^[a-zA-Z]:.*"))
         throw new IllegalArgumentException(
            "Caminho absoluto não é permitido (informe um caminho relativo à raiz): " +
            relativePath);

      final Path parsed;
      try {
         parsed = Path.of(trimmed);
      }
      catch (InvalidPathException ex) {
         throw new IllegalArgumentException(
            "Caminho inválido: " + relativePath, ex);
      }

      if (parsed.isAbsolute() || parsed.getRoot() != null)
         throw new IllegalArgumentException(
            "Caminho absoluto não é permitido (informe um caminho relativo à raiz): " +
            relativePath);

      // Percorre cada segmento para impedir travessia de diretório ('..')
      for (Path segment : parsed) {
         String segStr = segment.toString();
         if ("..".equals(segStr))
            throw new IllegalArgumentException(
               "Navegação para diretório superior ('..') não é permitida: " +
               relativePath);
      }

      Path normalized = root.resolve(parsed).normalize();
      if (!normalized.startsWith(root))
         throw new IllegalArgumentException(
            "Caminho fora do diretório autorizado: " + relativePath);

      if (isAppDataPath(normalized))
         throw new IllegalArgumentException(
            "Acesso à pasta interna '" + APP_DATA_DIR_NAME +
            "' não é permitido.");

      verifyNoSymlinksAndBounds(normalized);
      return normalized;
   }

   /**
    * Resolve e valida que o caminho exista e seja um diretório regular (sem symlink).
    */
   public Path resolveExistingDirectory(String relativePath) {
      Path target = resolve(relativePath);
      if (!Files.exists(target, LinkOption.NOFOLLOW_LINKS))
         throw new IllegalArgumentException(
            "Diretório não encontrado: " + displayPath(relativePath));

      if (!Files.isDirectory(target, LinkOption.NOFOLLOW_LINKS))
         throw new IllegalArgumentException(
            "O caminho informado não é um diretório: " +
            displayPath(relativePath));

      return target;
   }

   /**
    * Resolve e valida que o caminho exista e seja um arquivo regular (sem symlink).
    */
   public Path resolveExistingFile(String relativePath) {
      if (relativePath == null || relativePath.isBlank())
         throw new IllegalArgumentException("O caminho do arquivo é obrigatório.");

      Path target = resolve(relativePath);
      if (!Files.exists(target, LinkOption.NOFOLLOW_LINKS))
         throw new IllegalArgumentException(
            "Arquivo não encontrado: " + relativePath);

      if (!Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS))
         throw new IllegalArgumentException(
            "O caminho informado não é um arquivo regular: " + relativePath);

      return target;
   }

   /**
    * Verifica se um caminho pertence ou está dentro de {@code <raiz>/.mcp-organizer}.
    */
   public boolean isAppDataPath(Path path) {
      if (path == null)
         return false;
      Path normalized = path.toAbsolutePath().normalize();
      if (normalized.startsWith(root)) {
         Path rel = root.relativize(normalized);
         if (rel.getNameCount() > 0 &&
             APP_DATA_DIR_NAME.equalsIgnoreCase(rel.getName(0).toString()))
            return true;
      }
      for (Path part : normalized) {
         if (APP_DATA_DIR_NAME.equalsIgnoreCase(part.toString()))
            return true;
      }
      return false;
   }

   /**
    * Converte um caminho absoluto dentro da raiz para formato relativo usando {@code /}.
    */
   public String relativize(Path path) {
      if (path == null)
         throw new IllegalArgumentException("Caminho não pode ser nulo.");
      Path normalized = path.toAbsolutePath().normalize();
      if (!normalized.startsWith(root))
         throw new IllegalArgumentException(
            "Caminho fora do diretório autorizado: " + path);
      if (normalized.equals(root))
         return ".";
      return root.relativize(normalized).toString().replace('\\', '/');
   }

   // Valida cada segmento existente do caminho para garantir que não há symlinks nem fuga da raiz
   private void verifyNoSymlinksAndBounds(Path normalizedTarget) {
      Path rel = root.relativize(normalizedTarget);
      Path current = root;
      for (Path part : rel) {
         current = current.resolve(part);
         if (Files.exists(current, LinkOption.NOFOLLOW_LINKS)) {
            if (Files.isSymbolicLink(current))
               throw new IllegalArgumentException(
                  "Links simbólicos não são permitidos dentro do diretório autorizado: " +
                  rel);
            try {
               Path realCurrent = current.toRealPath(LinkOption.NOFOLLOW_LINKS);
               if (!realCurrent.startsWith(root))
                  throw new IllegalArgumentException(
                     "Caminho resolve para fora do diretório autorizado: " + rel);
            }
            catch (IOException ex) {
               throw new IllegalArgumentException(
                  "Não foi possível validar o caminho real: " + rel, ex);
            }
         }
      }
   }

   private static String displayPath(String relativePath) {
      return (relativePath == null || relativePath.isBlank())
             ? "."
             : relativePath.trim();
   }
}
