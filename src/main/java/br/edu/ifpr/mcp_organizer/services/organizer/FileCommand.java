package br.edu.ifpr.mcp_organizer.services.organizer;

import java.io.IOException;

/**
 * Interface Command (padrão de projeto Command) para operações de arquivo
 * reversíveis com {@link #execute()} e {@link #undo()}.
 */
public interface FileCommand {

   /**
    * Executa a operação no sistema de arquivos.
    *
    * @throws IOException se ocorrer falha de entrada/saída
    */
   void execute() throws IOException;

   /**
    * Reverte a operação realizada por {@link #execute()}.
    *
    * @throws IOException se ocorrer falha de entrada/saída
    */
   void undo() throws IOException;
}
