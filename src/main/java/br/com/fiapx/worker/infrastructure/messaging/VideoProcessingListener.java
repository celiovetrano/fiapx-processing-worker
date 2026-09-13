package br.com.fiapx.worker.infrastructure.messaging;

import br.com.fiapx.contracts.ContractsJson;
import br.com.fiapx.contracts.EventEnvelope;
import br.com.fiapx.contracts.VideoUploadedPayload;
import br.com.fiapx.worker.application.usecase.ProcessVideoUseCase;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.awspring.cloud.sqs.annotation.SqsListener;
import io.awspring.cloud.sqs.listener.SqsHeaders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Component
class VideoProcessingListener {

    private static final Logger log = LoggerFactory.getLogger(VideoProcessingListener.class);

    private final ProcessVideoUseCase useCase;
    private final ObjectMapper mapper = ContractsJson.mapper();

    VideoProcessingListener(ProcessVideoUseCase useCase) {
        this.useCase = useCase;
    }

    // O Spring Cloud AWS publica os atributos de sistema com prefixo: o header é
    // "Sqs_Msa_ApproximateReceiveCount". Com "ApproximateReceiveCount" a tentativa seria sempre 1.
    @SqsListener("${fiapx.sqs.processing-queue}")
    void onMessage(String body,
                   @Header(name = SqsHeaders.MessageSystemAttributes.SQS_APPROXIMATE_RECEIVE_COUNT,
                           required = false) String receiveCount) {
        try {
            EventEnvelope<VideoUploadedPayload> envelope =
                    mapper.readValue(body, new TypeReference<>() {});

            int tentativa = receiveCount == null ? 1 : Integer.parseInt(receiveCount);
            useCase.execute(envelope.payload(), tentativa);

        } catch (Exception ex) {
            // Mensagem ilegível: registrar e descartar. Devolver à fila só a faria
            // circular até cair na DLQ sem nunca ser processável.
            log.error("Mensagem descartada por payload invalido: {}", body, ex);
        }
    }
}
