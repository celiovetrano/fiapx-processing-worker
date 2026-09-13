package br.com.fiapx.worker.infrastructure.messaging;

import br.com.fiapx.contracts.ContractsJson;
import br.com.fiapx.contracts.ErrorCode;
import br.com.fiapx.contracts.EventEnvelope;
import br.com.fiapx.contracts.EventType;
import br.com.fiapx.contracts.VideoFailedPayload;
import br.com.fiapx.contracts.VideoProcessedPayload;
import br.com.fiapx.contracts.VideoProcessingStartedPayload;
import br.com.fiapx.worker.application.port.out.ProcessingEventPublisher;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.awspring.cloud.sns.core.SnsTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
class SnsProcessingEventPublisher implements ProcessingEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(SnsProcessingEventPublisher.class);

    private final SnsTemplate snsTemplate;
    private final ObjectMapper mapper = ContractsJson.mapper();
    private final String topic;

    SnsProcessingEventPublisher(SnsTemplate snsTemplate,
                                @Value("${fiapx.sns.events-topic}") String topic) {
        this.snsTemplate = snsTemplate;
        this.topic = topic;
    }

    @Override
    public void publishStarted(UUID videoId, UUID userId, int attempt) {
        publish(EventType.VIDEO_PROCESSING_STARTED, videoId,
                new VideoProcessingStartedPayload(videoId, userId, hostname(), attempt));
    }

    @Override
    public void publishProcessed(UUID videoId, UUID userId, String s3ZipKey,
                                 int frameCount, long millis) {
        publish(EventType.VIDEO_PROCESSED, videoId,
                new VideoProcessedPayload(videoId, userId, s3ZipKey, frameCount, millis));
    }

    @Override
    public void publishFailed(UUID videoId, UUID userId, String userEmail,
                              ErrorCode errorCode, String errorMessage, int attempt) {
        publish(EventType.VIDEO_FAILED, videoId,
                new VideoFailedPayload(videoId, userId, userEmail, errorCode, errorMessage, attempt));
    }

    private void publish(EventType tipo, UUID videoId, Object payload) {
        var envelope = EventEnvelope.of(tipo, videoId, payload);
        try {
            snsTemplate.sendNotification(topic, mapper.writeValueAsString(envelope), null);
            log.info("Evento {} publicado correlationId={}", tipo.wireName(), videoId);
        } catch (Exception ex) {
            throw new IllegalStateException(
                    "Falha ao publicar " + tipo.wireName() + " para " + videoId, ex);
        }
    }

    private static String hostname() {
        try {
            return java.net.InetAddress.getLocalHost().getHostName();
        } catch (Exception ex) {
            return "desconhecido";
        }
    }
}
