package br.com.fiapx.worker.infrastructure.storage;

import br.com.fiapx.contracts.ErrorCode;
import br.com.fiapx.worker.application.exception.ProcessingException;
import br.com.fiapx.worker.application.port.out.VideoObjectStorage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.nio.file.Path;
import java.util.UUID;

@Component
class S3VideoObjectStorage implements VideoObjectStorage {

    private final S3Client s3;
    private final String bucket;

    S3VideoObjectStorage(S3Client s3, @Value("${fiapx.s3.bucket}") String bucket) {
        this.s3 = s3;
        this.bucket = bucket;
    }

    @Override
    public void download(String key, Path target) {
        try {
            s3.getObject(GetObjectRequest.builder().bucket(bucket).key(key).build(), target);
        } catch (RuntimeException ex) {
            throw new ProcessingException(ErrorCode.STORAGE_FAILURE,
                    "Falha ao baixar o objeto " + key, ex);
        }
    }

    @Override
    public String uploadZip(UUID userId, UUID videoId, Path zip) {
        String key = "processed/%s/%s.zip".formatted(userId, videoId);
        try {
            s3.putObject(PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .contentType("application/zip")
                    .build(), RequestBody.fromFile(zip));
            return key;
        } catch (RuntimeException ex) {
            throw new ProcessingException(ErrorCode.STORAGE_FAILURE,
                    "Falha ao enviar o ZIP " + key, ex);
        }
    }
}
