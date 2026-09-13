package br.com.fiapx.worker.infrastructure.storage;

import br.com.fiapx.worker.application.port.out.VideoObjectStorage;
import br.com.fiapx.worker.support.LocalStackTestContainer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(LocalStackTestContainer.class)
class S3VideoObjectStorageIT {

    @Autowired VideoObjectStorage storage;
    @Autowired S3Client s3;

    @Value("${fiapx.s3.bucket}")
    String bucket;

    @BeforeEach
    void criarBucket() {
        try {
            s3.createBucket(b -> b.bucket(bucket));
        } catch (RuntimeException ignorado) {
            // bucket já existe entre testes
        }
    }

    @Test
    void baixaOObjetoParaOCaminhoInformado(@TempDir Path tmp) throws Exception {
        String key = "raw/" + UUID.randomUUID() + "/v.mp4";
        s3.putObject(b -> b.bucket(bucket).key(key), RequestBody.fromString("bytes-do-video"));
        Path destino = tmp.resolve("baixado.mp4");

        storage.download(key, destino);

        assertThat(Files.readString(destino)).isEqualTo("bytes-do-video");
    }

    @Test
    void enviaOZipParaAChaveProcessed(@TempDir Path tmp) throws Exception {
        UUID userId = UUID.randomUUID();
        UUID videoId = UUID.randomUUID();
        Path zip = Files.writeString(tmp.resolve("frames.zip"), "conteudo-zip");

        String key = storage.uploadZip(userId, videoId, zip);

        assertThat(key).isEqualTo("processed/" + userId + "/" + videoId + ".zip");
        assertThat(new String(s3.getObjectAsBytes(b -> b.bucket(bucket).key(key)).asByteArray(),
                StandardCharsets.UTF_8)).isEqualTo("conteudo-zip");
    }
}
