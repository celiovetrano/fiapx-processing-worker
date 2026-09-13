package br.com.fiapx.worker.infrastructure.ffmpeg;

import br.com.fiapx.contracts.ErrorCode;
import br.com.fiapx.worker.application.exception.ProcessingException;
import br.com.fiapx.worker.application.port.out.FrameExtractor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

@Component
public class FfmpegFrameExtractor implements FrameExtractor {

    private static final Logger log = LoggerFactory.getLogger(FfmpegFrameExtractor.class);
    private static final int MAX_LOG_CHARS = 2000;

    private final String binary;
    private final long timeoutSeconds;

    public FfmpegFrameExtractor(@Value("${fiapx.ffmpeg.binary}") String binary,
                                @Value("${fiapx.ffmpeg.timeout-seconds}") long timeoutSeconds) {
        this.binary = binary;
        this.timeoutSeconds = timeoutSeconds;
    }

    @Override
    public List<Path> extract(Path video, Path outputDir) {
        Path padrao = outputDir.resolve("frame_%04d.png");
        // A saída vai para um arquivo, e não para o pipe: num vídeo longo o pipe enche,
        // o ffmpeg bloqueia na escrita e o waitFor estouraria como um TIMEOUT falso.
        Path logFfmpeg = outputDir.resolve("ffmpeg.log");

        ProcessBuilder builder = new ProcessBuilder(
                binary, "-nostdin", "-i", video.toAbsolutePath().toString(),
                "-vf", "fps=1", "-y", padrao.toAbsolutePath().toString());
        builder.redirectErrorStream(true);
        builder.redirectOutput(logFfmpeg.toFile());

        Process processo = null;
        try {
            processo = builder.start();

            if (!processo.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                processo.destroyForcibly();
                throw new ProcessingException(ErrorCode.TIMEOUT,
                        "ffmpeg excedeu " + timeoutSeconds + "s");
            }

            if (processo.exitValue() != 0) {
                log.warn("ffmpeg falhou com codigo {}: {}", processo.exitValue(), finalDoLog(logFfmpeg));
                throw new ProcessingException(ErrorCode.FFMPEG_FAILURE,
                        "ffmpeg retornou codigo " + processo.exitValue());
            }

            List<Path> frames = listarFrames(outputDir);
            if (frames.isEmpty()) {
                throw new ProcessingException(ErrorCode.NO_FRAMES_EXTRACTED,
                        "Nenhum frame foi extraido do video");
            }
            log.info("Extraidos {} frames de {}", frames.size(), video.getFileName());
            return frames;

        } catch (IOException ex) {
            throw new ProcessingException(ErrorCode.FFMPEG_FAILURE,
                    "Falha ao executar o ffmpeg: " + ex.getMessage(), ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            if (processo != null) {
                processo.destroyForcibly();
            }
            throw new ProcessingException(ErrorCode.TIMEOUT, "Processamento interrompido", ex);
        }
    }

    private List<Path> listarFrames(Path outputDir) throws IOException {
        try (Stream<Path> arquivos = Files.list(outputDir)) {
            return arquivos
                    .filter(p -> p.getFileName().toString().endsWith(".png"))
                    .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .toList();
        }
    }

    private static String finalDoLog(Path logFfmpeg) {
        try {
            String conteudo = Files.readString(logFfmpeg, StandardCharsets.UTF_8);
            return conteudo.length() <= MAX_LOG_CHARS
                    ? conteudo
                    : conteudo.substring(conteudo.length() - MAX_LOG_CHARS);
        } catch (IOException ex) {
            return "(log do ffmpeg indisponivel)";
        }
    }
}
