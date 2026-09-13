package br.com.fiapx.worker.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.utility.DockerImageName;

import static org.testcontainers.containers.localstack.LocalStackContainer.Service.S3;
import static org.testcontainers.containers.localstack.LocalStackContainer.Service.SNS;
import static org.testcontainers.containers.localstack.LocalStackContainer.Service.SQS;

/**
 * Publica o endpoint do LocalStack como propriedades spring.cloud.aws.*, e não via
 * {@code @ServiceConnection}: o Boot não tem ConnectionDetails para o LocalStack.
 */
@TestConfiguration(proxyBeanMethods = false)
public class LocalStackTestContainer {

    @Bean
    LocalStackContainer localStack(DynamicPropertyRegistry registry) {
        LocalStackContainer container = new LocalStackContainer(DockerImageName.parse("localstack/localstack:3.8"))
                .withServices(S3, SQS, SNS);
        registry.add("spring.cloud.aws.endpoint", () -> container.getEndpoint().toString());
        registry.add("spring.cloud.aws.region.static", container::getRegion);
        registry.add("spring.cloud.aws.credentials.access-key", container::getAccessKey);
        registry.add("spring.cloud.aws.credentials.secret-key", container::getSecretKey);
        registry.add("spring.cloud.aws.s3.path-style-access-enabled", () -> "true");
        return container;
    }
}
