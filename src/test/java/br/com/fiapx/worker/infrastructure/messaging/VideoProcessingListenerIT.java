package br.com.fiapx.worker.infrastructure.messaging;

import br.com.fiapx.contracts.ContractsJson;
import br.com.fiapx.contracts.EventEnvelope;
import br.com.fiapx.contracts.EventType;
import br.com.fiapx.contracts.VideoUploadedPayload;
import br.com.fiapx.worker.support.LocalStackTestContainer;
import io.awspring.cloud.sqs.operations.SqsTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;

import java.io.InputStream;
import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@Import(LocalStackTestContainer.class)
class VideoProcessingListenerIT {

    @Autowired SqsTemplate sqsTemplate;
    @Autowired SqsAsyncClient sqs; // o spring-cloud-aws só autoconfigura o cliente assíncrono
    @Autowired SnsClient sns;
    @Autowired S3Client s3;

    @Value("${fiapx.sqs.processing-queue}") String fila;
    @Value("${fiapx.sns.events-topic}") String topico;
    @Value("${fiapx.s3.bucket}") String bucket;

    @BeforeEach
    void prepararRecursos() {
        try {
            s3.createBucket(b -> b.bucket(bucket));
        } catch (RuntimeException ignorado) {
            // já existe
        }
        sqs.createQueue(b -> b.queueName(fila)).join();
        sns.createTopic(b -> b.name(topico));
    }

    @Test
    void consomeVideoUploadedEGravaOZipNoS3() throws Exception {
        UUID videoId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String rawKey = "raw/" + userId + "/" + videoId + ".mp4";

        try (InputStream fixture = getClass().getResourceAsStream("/fixtures/sample-2s.mp4")) {
            byte[] bytes = fixture.readAllBytes();
            s3.putObject(b -> b.bucket(bucket).key(rawKey), RequestBody.fromBytes(bytes));
        }

        var envelope = EventEnvelope.of(EventType.VIDEO_UPLOADED, videoId,
                new VideoUploadedPayload(videoId, userId, "aluno@fiap.com.br",
                        rawKey, "sample-2s.mp4", 15_000L));

        // Serializa fora do lambda: a JsonProcessingException é checada e o Consumer não a propaga.
        String json = ContractsJson.mapper().writeValueAsString(envelope);
        sqsTemplate.send(to -> to.queue(fila).payload(json));

        String zipKey = "processed/" + userId + "/" + videoId + ".zip";
        // untilAsserted só repete em AssertionError: sem ignorar o 404 do S3, a primeira
        // leitura antes do ZIP existir derrubaria o teste em vez de esperar.
        await().atMost(Duration.ofSeconds(60))
                .ignoreException(NoSuchKeyException.class)
                .untilAsserted(() ->
                assertThat(s3.getObjectAsBytes(b -> b.bucket(bucket).key(zipKey)).asByteArray())
                        .isNotEmpty());
    }
}
