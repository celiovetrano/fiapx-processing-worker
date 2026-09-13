package br.com.fiapx.worker.application.port.out;

import br.com.fiapx.contracts.ErrorCode;

import java.util.UUID;

public interface ProcessingEventPublisher {

    void publishStarted(UUID videoId, UUID userId, int attempt);

    void publishProcessed(UUID videoId, UUID userId, String s3ZipKey, int frameCount, long millis);

    void publishFailed(UUID videoId, UUID userId, String userEmail,
                       ErrorCode errorCode, String errorMessage, int attempt);
}
