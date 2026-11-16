package br.edu.ifpr.mcp_organizer.services.organizer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;

/**
 * Implementação do padrão Command ({@link FileCommand}) para mover um arquivo
 * sem sobrescrever o destino e permitir desfazer o movimento retornando-o ao
 * caminho de origem.
 */
public class MoveFileCommand implements FileCommand {

   private final Path source;
   private final Path target;

   public MoveFileCommand(Path source, Path target) {
      if (source == null || target == null)
         throw new IllegalArgumentException(
            "Os caminhos de origem e destino não podem ser nulos.");
      this.source = source;
      this.target = target;
   }

   @Override
   public void execute() throws IOException {
      if (!Files.exists(source, LinkOption.NOFOLLOW_LINKS))
         throw new IOException("Arquivo de origem não existe: " + source);

      if (Files.exists(target, LinkOption.NOFOLLOW_LINKS))
         throw new IOException(
            "Destino já ocupado (sobrescrita proibida): " + target);

      Path parent = target.getParent();
      if (parent != null)
         Files.createDirectories(parent);

      // Sem REPLACE_EXISTING para garantir a regra de negócio de nunca sobrescrever
      Files.move(source, target);
   }

   @Override
   public void undo() throws IOException {
      if (!Files.exists(target, LinkOption.NOFOLLOW_LINKS))
         throw new IOException(
            "Arquivo movido não foi encontrado no destino para reversão: " +
            target);

      if (Files.exists(source, LinkOption.NOFOLLOW_LINKS))
         throw new IOException(
            "Local original ocupado; reversão abortada para não sobrescrever: " +
            source);

      Path originalParent = source.getParent();
      if (originalParent != null)
         Files.createDirectories(originalParent);

      Files.move(target, source);
   }

   public Path getSource() {
      return source;
   }

   public Path getTarget() {
      return target;
   }
}
