package br.com.fiapx.worker.infrastructure.archive;

import br.com.fiapx.contracts.ErrorCode;
import br.com.fiapx.worker.application.exception.ProcessingException;
import br.com.fiapx.worker.application.port.out.ArchiveWriter;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Component
public class ZipArchiveWriter implements ArchiveWriter {

    @Override
    public void write(List<Path> files, Path target) {
        List<Path> ordenados = files.stream()
                .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                .toList();

        try (OutputStream out = Files.newOutputStream(target);
             ZipOutputStream zip = new ZipOutputStream(out)) {

            zip.setMethod(ZipOutputStream.DEFLATED);

            for (Path arquivo : ordenados) {
                ZipEntry entrada = new ZipEntry(arquivo.getFileName().toString());
                entrada.setMethod(ZipEntry.DEFLATED);
                zip.putNextEntry(entrada);
                Files.copy(arquivo, zip);
                zip.closeEntry();
            }
        } catch (IOException ex) {
            throw new ProcessingException(ErrorCode.STORAGE_FAILURE,
                    "Falha ao criar o arquivo ZIP", ex);
        }
    }
}
