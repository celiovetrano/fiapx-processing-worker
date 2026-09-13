package br.com.fiapx.worker.application.port.out;

import java.nio.file.Path;
import java.util.List;

public interface FrameExtractor {

    /** Extrai 1 frame por segundo em PNG. @return os frames, ordenados pelo nome. */
    List<Path> extract(Path video, Path outputDir);
}
