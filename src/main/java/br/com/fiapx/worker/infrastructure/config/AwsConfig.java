package br.com.fiapx.worker.infrastructure.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Configuration
public class AwsConfig {

    /** Diretório base de trabalho, criado na subida para falhar cedo se não houver permissão. */
    @Bean
    Path workDir(@Value("${fiapx.work-dir}") String workDir) throws IOException {
        return Files.createDirectories(Path.of(workDir));
    }
}
