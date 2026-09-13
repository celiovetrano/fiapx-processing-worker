package br.com.fiapx.worker.application.port.out;

import java.nio.file.Path;
import java.util.List;

public interface ArchiveWriter {
    void write(List<Path> files, Path target);
}
