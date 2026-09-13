package br.com.fiapx.worker.infrastructure.archive;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.assertj.core.api.Assertions.assertThat;

class ZipArchiveWriterTest {

    private final ZipArchiveWriter writer = new ZipArchiveWriter();

    @Test
    void compactaTodosOsArquivosSemDiretorios(@TempDir Path tmp) throws Exception {
        Path pastaFrames = Files.createDirectory(tmp.resolve("frames"));
        Path frame1 = Files.writeString(pastaFrames.resolve("frame_0001.png"), "png-1");
        Path frame2 = Files.writeString(pastaFrames.resolve("frame_0002.png"), "png-2");
        Path destino = tmp.resolve("frames.zip");

        writer.write(List.of(frame1, frame2), destino);

        List<String> nomes = new ArrayList<>();
        try (ZipFile zip = new ZipFile(destino.toFile())) {
            var entradas = zip.entries();
            while (entradas.hasMoreElements()) {
                ZipEntry entrada = entradas.nextElement();
                nomes.add(entrada.getName());
                assertThat(entrada.getMethod()).isEqualTo(ZipEntry.DEFLATED);
            }
            assertThat(new String(zip.getInputStream(zip.getEntry("frame_0001.png")).readAllBytes(),
                    StandardCharsets.UTF_8)).isEqualTo("png-1");
        }

        assertThat(nomes).containsExactlyInAnyOrder("frame_0001.png", "frame_0002.png");
        assertThat(nomes).allSatisfy(nome -> assertThat(nome).doesNotContain("/"));
    }

    @Test
    void ordenaAsEntradasPeloNome(@TempDir Path tmp) throws Exception {
        Path pasta = Files.createDirectory(tmp.resolve("frames"));
        Path terceiro = Files.writeString(pasta.resolve("frame_0003.png"), "c");
        Path primeiro = Files.writeString(pasta.resolve("frame_0001.png"), "a");
        Path destino = tmp.resolve("out.zip");

        writer.write(List.of(terceiro, primeiro), destino);

        try (ZipFile zip = new ZipFile(destino.toFile())) {
            assertThat(zip.stream().map(ZipEntry::getName).toList())
                    .containsExactly("frame_0001.png", "frame_0003.png");
        }
    }
}
