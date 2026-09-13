package br.com.fiapx.worker.application.usecase;

import br.com.fiapx.contracts.ErrorCode;
import br.com.fiapx.contracts.VideoUploadedPayload;
import br.com.fiapx.worker.application.exception.ProcessingException;
import br.com.fiapx.worker.application.port.out.ArchiveWriter;
import br.com.fiapx.worker.application.port.out.FrameExtractor;
import br.com.fiapx.worker.application.port.out.ProcessingEventPublisher;
import br.com.fiapx.worker.application.port.out.VideoObjectStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

@Service
public class ProcessVideoUseCase {

    private static final Logger log = LoggerFactory.getLogger(ProcessVideoUseCase.class);

    private final VideoObjectStorage storage;
    private final FrameExtractor extractor;
    private final ArchiveWriter archiveWriter;
    private final ProcessingEventPublisher publisher;
    private final Path workDir;

    public ProcessVideoUseCase(VideoObjectStorage storage, FrameExtractor extractor,
                               ArchiveWriter archiveWriter, ProcessingEventPublisher publisher,
                               Path workDir) {
        this.storage = storage;
        this.extractor = extractor;
        this.archiveWriter = archiveWriter;
        this.publisher = publisher;
        this.workDir = workDir;
    }

    /**
     * Nunca propaga exceção: toda falha vira um VideoFailedEvent. Deixar a exceção
     * escapar faria o SQS devolver a mensagem à fila e reprocessar um vídeo que já
     * se sabe defeituoso.
     */
    public void execute(VideoUploadedPayload payload, int attempt) {
        long inicio = System.currentTimeMillis();
        Path diretorio = null;

        try {
            publisher.publishStarted(payload.videoId(), payload.userId(), attempt);

            diretorio = Files.createTempDirectory(workDir, "video-" + payload.videoId() + "-");
            Path video = diretorio.resolve("entrada" + extensaoDe(payload.originalFilename()));
            Path frames = Files.createDirectory(diretorio.resolve("frames"));
            Path zip = diretorio.resolve("frames.zip");

            storage.download(payload.s3RawKey(), video);
            List<Path> extraidos = extractor.extract(video, frames);
            archiveWriter.write(extraidos, zip);
            String zipKey = storage.uploadZip(payload.userId(), payload.videoId(), zip);

            long duracao = System.currentTimeMillis() - inicio;
            publisher.publishProcessed(payload.videoId(), payload.userId(),
                    zipKey, extraidos.size(), duracao);
            log.info("Video {} processado: {} frames em {} ms",
                    payload.videoId(), extraidos.size(), duracao);

        } catch (ProcessingException ex) {
            log.warn("Falha ao processar {}: {}", payload.videoId(), ex.getMessage());
            publisher.publishFailed(payload.videoId(), payload.userId(), payload.userEmail(),
                    ex.errorCode(), ex.getMessage(), attempt);

        } catch (Exception ex) {
            log.error("Falha inesperada ao processar {}", payload.videoId(), ex);
            publisher.publishFailed(payload.videoId(), payload.userId(), payload.userEmail(),
                    ErrorCode.UNKNOWN, String.valueOf(ex.getMessage()), attempt);

        } finally {
            apagar(diretorio);
        }
    }

    private static String extensaoDe(String filename) {
        int ponto = filename.lastIndexOf('.');
        return ponto < 0 ? "" : filename.substring(ponto);
    }

    private void apagar(Path diretorio) {
        if (diretorio == null || !Files.exists(diretorio)) {
            return;
        }
        try (Stream<Path> caminhos = Files.walk(diretorio)) {
            caminhos.sorted(Comparator.reverseOrder()).forEach(caminho -> {
                try {
                    Files.deleteIfExists(caminho);
                } catch (IOException ex) {
                    log.warn("Nao foi possivel apagar {}", caminho, ex);
                }
            });
        } catch (IOException ex) {
            log.warn("Falha ao limpar o diretorio temporario {}", diretorio, ex);
        }
    }
}
