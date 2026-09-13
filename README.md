# fiapx-processing-worker

Consome `video-processing-queue`, extrai 1 frame por segundo com ffmpeg, compacta
em ZIP e publica o resultado no tópico SNS `video-events`. Não expõe API — apenas
`/actuator` na porta 8083.

É este serviço que escala horizontalmente: N réplicas consomem a mesma fila.

## Rodar os testes

    ./mvnw verify

Requer Docker (LocalStack) e `ffmpeg` no PATH.

## Variáveis de ambiente

| Variável | Padrão | Descrição |
|---|---|---|
| `S3_BUCKET` | `fiapx-videos` | Bucket |
| `SQS_PROCESSING_QUEUE` | `video-processing-queue` | Fila de entrada |
| `SNS_EVENTS_TOPIC` | `video-events` | Tópico de saída |
| `FFMPEG_BINARY` | `ffmpeg` | Caminho do executável do ffmpeg |
| `FFMPEG_TIMEOUT_SECONDS` | `600` | Timeout do processo |
| `WORK_DIR` | `${java.io.tmpdir}/fiapx` | Diretório temporário (`/tmp/fiapx` no container) |
| `AWS_ENDPOINT` | vazio | Aponte para o LocalStack no ambiente local |
| `S3_PATH_STYLE` | `false` | `true` no LocalStack, onde o host virtual do bucket não resolve |
