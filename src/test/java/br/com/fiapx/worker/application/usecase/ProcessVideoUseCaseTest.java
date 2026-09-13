package br.com.fiapx.worker.application.usecase;

import br.com.fiapx.contracts.ErrorCode;
import br.com.fiapx.contracts.VideoUploadedPayload;
import br.com.fiapx.worker.application.exception.ProcessingException;
import br.com.fiapx.worker.application.port.out.ArchiveWriter;
import br.com.fiapx.worker.application.port.out.FrameExtractor;
import br.com.fiapx.worker.application.port.out.ProcessingEventPublisher;
import br.com.fiapx.worker.application.port.out.VideoObjectStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InOrder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProcessVideoUseCaseTest {

    private VideoObjectStorage storage;
    private FrameExtractor extractor;
    private ArchiveWriter archiveWriter;
    private ProcessingEventPublisher publisher;
    private ProcessVideoUseCase useCase;

    private final UUID videoId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private Path baseDir;

    private VideoUploadedPayload payload() {
        return new VideoUploadedPayload(videoId, userId, "aluno@fiap.com.br",
                "raw/u/v.mp4", "clipe.mp4", 2048L);
    }

    @BeforeEach
    void setUp(@TempDir Path tmp) {
        baseDir = tmp;
        storage = mock(VideoObjectStorage.class);
        extractor = mock(FrameExtractor.class);
        archiveWriter = mock(ArchiveWriter.class);
        publisher = mock(ProcessingEventPublisher.class);
        useCase = new ProcessVideoUseCase(storage, extractor, archiveWriter, publisher, tmp);

        when(extractor.extract(any(), any())).thenAnswer(invocacao -> {
            Path saida = invocacao.getArgument(1);
            return List.of(
                    Files.writeString(saida.resolve("frame_0001.png"), "a"),
                    Files.writeString(saida.resolve("frame_0002.png"), "b"));
        });
        when(storage.uploadZip(eq(userId), eq(videoId), any())).thenReturn("processed/u/v.zip");
    }

    @Test
    void caminhoFelizPublicaStartedEProcessedNaOrdem() {
        useCase.execute(payload(), 1);

        InOrder ordem = inOrder(publisher, storage, extractor, archiveWriter);
        ordem.verify(publisher).publishStarted(videoId, userId, 1);
        ordem.verify(storage).download(eq("raw/u/v.mp4"), any());
        ordem.verify(extractor).extract(any(), any());
        ordem.verify(archiveWriter).write(any(), any());
        ordem.verify(storage).uploadZip(eq(userId), eq(videoId), any());
        ordem.verify(publisher).publishProcessed(eq(videoId), eq(userId),
                eq("processed/u/v.zip"), eq(2), anyLong());
    }

    @Test
    void falhaDoFfmpegViraVideoFailedComOMesmoErrorCode() {
        // doThrow, e não when(...): when() chamaria o thenAnswer do setUp com argumentos nulos.
        doThrow(new ProcessingException(ErrorCode.FFMPEG_FAILURE, "codec invalido"))
                .when(extractor).extract(any(), any());

        useCase.execute(payload(), 2);

        verify(publisher).publishFailed(videoId, userId, "aluno@fiap.com.br",
                ErrorCode.FFMPEG_FAILURE, "codec invalido", 2);
        verify(publisher, never()).publishProcessed(any(), any(), anyString(), anyInt(), anyLong());
    }

    @Test
    void erroInesperadoViraUnknownEmVezDeVazarExcecao() {
        doThrow(new IllegalStateException("boom")).when(storage).download(anyString(), any());

        useCase.execute(payload(), 1);

        verify(publisher).publishFailed(eq(videoId), eq(userId), eq("aluno@fiap.com.br"),
                eq(ErrorCode.UNKNOWN), anyString(), eq(1));
    }

    @Test
    void removeODiretorioTemporarioMesmoEmCasoDeFalha() throws Exception {
        doThrow(new ProcessingException(ErrorCode.TIMEOUT, "estourou"))
                .when(extractor).extract(any(), any());

        useCase.execute(payload(), 1);

        try (var conteudo = Files.list(baseDir)) {
            assertThat(conteudo).isEmpty();
        }
    }

    @Test
    void removeODiretorioTemporarioNoCaminhoFeliz() throws Exception {
        useCase.execute(payload(), 1);

        try (var conteudo = Files.list(baseDir)) {
            assertThat(conteudo).isEmpty();
        }
    }
}
