package br.com.fiapx.worker.infrastructure.ffmpeg;

import br.com.fiapx.contracts.ErrorCode;
import br.com.fiapx.worker.application.exception.ProcessingException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FfmpegFrameExtractorTest {

    private final FfmpegFrameExtractor extractor = new FfmpegFrameExtractor("ffmpeg", 600);

    private Path fixture(Path tmp) throws Exception {
        Path destino = tmp.resolve("sample-2s.mp4");
        try (var in = getClass().getResourceAsStream("/fixtures/sample-2s.mp4")) {
            Files.copy(in, destino);
        }
        return destino;
    }

    @Test
    void extraiUmFramePorSegundoEmPng(@TempDir Path tmp) throws Exception {
        Path saida = Files.createDirectory(tmp.resolve("frames"));

        List<Path> frames = extractor.extract(fixture(tmp), saida);

        assertThat(frames).hasSize(2);
        assertThat(frames.get(0).getFileName().toString()).isEqualTo("frame_0001.png");
        assertThat(frames.get(1).getFileName().toString()).isEqualTo("frame_0002.png");
        assertThat(Files.size(frames.get(0))).isPositive();
    }

    @Test
    void arquivoCorrompidoResultaEmFfmpegFailure(@TempDir Path tmp) throws Exception {
        Path quebrado = Files.writeString(tmp.resolve("quebrado.mp4"), "isto nao e um video");
        Path saida = Files.createDirectory(tmp.resolve("frames"));

        assertThatThrownBy(() -> extractor.extract(quebrado, saida))
                .isInstanceOf(ProcessingException.class)
                .satisfies(ex -> assertThat(((ProcessingException) ex).errorCode())
                        .isEqualTo(ErrorCode.FFMPEG_FAILURE));
    }

    @Test
    void timeoutZeroInterrompeOProcessoComTimeout(@TempDir Path tmp) throws Exception {
        var extractorImpaciente = new FfmpegFrameExtractor("ffmpeg", 0);
        Path saida = Files.createDirectory(tmp.resolve("frames"));

        assertThatThrownBy(() -> extractorImpaciente.extract(fixture(tmp), saida))
                .isInstanceOf(ProcessingException.class)
                .satisfies(ex -> assertThat(((ProcessingException) ex).errorCode())
                        .isEqualTo(ErrorCode.TIMEOUT));
    }

    @Test
    void binarioInexistenteResultaEmFfmpegFailure(@TempDir Path tmp) throws Exception {
        var extractorSemBinario = new FfmpegFrameExtractor("ffmpeg-que-nao-existe", 600);
        Path saida = Files.createDirectory(tmp.resolve("frames"));

        assertThatThrownBy(() -> extractorSemBinario.extract(fixture(tmp), saida))
                .isInstanceOf(ProcessingException.class);
    }
}
