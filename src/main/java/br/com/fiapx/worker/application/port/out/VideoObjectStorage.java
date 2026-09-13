package br.com.fiapx.worker.application.port.out;

import java.nio.file.Path;
import java.util.UUID;

public interface VideoObjectStorage {

    void download(String key, Path target);

    /** @return a chave S3 do ZIP gravado. */
    String uploadZip(UUID userId, UUID videoId, Path zip);
}
