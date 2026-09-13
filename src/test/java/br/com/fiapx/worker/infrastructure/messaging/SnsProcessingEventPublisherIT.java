package br.com.fiapx.worker.infrastructure.messaging;

import br.com.fiapx.contracts.ContractsJson;
import br.com.fiapx.contracts.ErrorCode;
import br.com.fiapx.contracts.EventEnvelope;
import br.com.fiapx.contracts.EventType;
import br.com.fiapx.contracts.VideoFailedPayload;
import br.com.fiapx.contracts.VideoProcessedPayload;
import br.com.fiapx.worker.application.port.out.ProcessingEventPublisher;
import br.com.fiapx.worker.support.LocalStackTestContainer;
import com.fasterxml.jackson.core.type.TypeReference;
import io.awspring.cloud.sqs.operations.SqsTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(LocalStackTestContainer.class)
class SnsProcessingEventPublisherIT {

    @Autowired ProcessingEventPublisher publisher;
    @Autowired SnsClient sns;
    @Autowired SqsAsyncClient sqs; // o spring-cloud-aws só autoconfigura o cliente assíncrono
    @Autowired SqsTemplate sqsTemplate;

    @Value("${fiapx.sns.events-topic}")
    String topico;

    private String filaDeProva;

    @BeforeEach
    void assinarFilaDeProva() {
        String topicArn = sns.createTopic(b -> b.name(topico)).topicArn();
        filaDeProva = "prova-" + UUID.randomUUID();
        String queueUrl = sqs.createQueue(b -> b.queueName(filaDeProva)).join().queueUrl();
        String queueArn = sqs.getQueueAttributes(b -> b.queueUrl(queueUrl)
                .attributeNamesWithStrings("QueueArn")).join().attributesAsStrings().get("QueueArn");

        sns.subscribe(b -> b.topicArn(topicArn).protocol("sqs").endpoint(queueArn)
                .attributes(Map.of("RawMessageDelivery", "true")));
    }

    private String receber() {
        return sqsTemplate.receive(from -> from.queue(filaDeProva)
                        .pollTimeout(Duration.ofSeconds(10)), String.class)
                .orElseThrow()
                .getPayload();
    }

    @Test
    void publicaVideoProcessed() throws Exception {
        UUID videoId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        publisher.publishProcessed(videoId, userId, "processed/u/v.zip", 42, 1500L);

        var envelope = ContractsJson.mapper().readValue(receber(),
                new TypeReference<EventEnvelope<VideoProcessedPayload>>() {});

        assertThat(envelope.eventType()).isEqualTo(EventType.VIDEO_PROCESSED);
        assertThat(envelope.correlationId()).isEqualTo(videoId);
        assertThat(envelope.payload().frameCount()).isEqualTo(42);
        assertThat(envelope.payload().s3ZipKey()).isEqualTo("processed/u/v.zip");
    }

    @Test
    void publicaVideoFailedComCodigoEEmailDoUsuario() throws Exception {
        UUID videoId = UUID.randomUUID();

        publisher.publishFailed(videoId, UUID.randomUUID(), "aluno@fiap.com.br",
                ErrorCode.FFMPEG_FAILURE, "codec invalido", 2);

        var envelope = ContractsJson.mapper().readValue(receber(),
                new TypeReference<EventEnvelope<VideoFailedPayload>>() {});

        assertThat(envelope.eventType()).isEqualTo(EventType.VIDEO_FAILED);
        assertThat(envelope.payload().errorCode()).isEqualTo(ErrorCode.FFMPEG_FAILURE);
        assertThat(envelope.payload().userEmail()).isEqualTo("aluno@fiap.com.br");
        assertThat(envelope.payload().attempt()).isEqualTo(2);
    }
}
