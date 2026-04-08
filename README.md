# floci-sqs-spring

Projeto de exemplo demonstrando integração com **AWS SNS/SQS** usando Spring Boot e [Floci](https://github.com/hectorvent/floci) como mock local da AWS.

## Tecnologias

- Java 21
- Spring Boot 3.4.4
- Spring Cloud AWS 3.3.0 (SQS + SNS)
- Floci (mock local AWS — porta 4566)
- Maven

## Arquitetura

O projeto implementa o padrão **SNS fan-out com roteamento por filtro via SQS**, aplicado ao domínio de pedidos.

```
POST /pedidos
  └─► PedidoController
        └─► PedidoProducer (publica no SNS com atributo "tipo")
              └─► pedidos-topic (SNS)
                    ├── FilterPolicy tipo=NOVO        ─► pedidos-novos (SQS)        ─► NovoPedidoConsumer
                    └── FilterPolicy tipo=CANCELAMENTO ─► pedidos-cancelados (SQS)  ─► CancelamentoPedidoConsumer
```

A infraestrutura (tópico SNS, filas SQS, assinaturas e políticas de filtro) é criada automaticamente na inicialização da aplicação pelo `SqsQueueInitializer`.

## Pré-requisitos

- Docker
- Java 21+
- Maven 3.9+

## Como executar

**1. Subir o Floci (mock da AWS):**
```bash
docker-compose up -d
```

**2. Iniciar a aplicação:**
```bash
mvn spring-boot:run
```

A aplicação estará disponível em `http://localhost:8080`.

## Testando a API

**Criar novo pedido:**
```bash
curl -X POST http://localhost:8080/pedidos \
  -H "Content-Type: application/json" \
  -d '{
    "pedidoId": 1,
    "produto": "Laptop",
    "quantidade": 2,
    "valorTotal": 5000.00,
    "tipo": "NOVO"
  }'
```

**Cancelar pedido:**
```bash
curl -X POST http://localhost:8080/pedidos \
  -H "Content-Type: application/json" \
  -d '{
    "pedidoId": 1,
    "produto": "Laptop",
    "quantidade": 2,
    "valorTotal": 5000.00,
    "tipo": "CANCELAMENTO"
  }'
```

## Detalhes do SNS

### Publicação de mensagens

O `PedidoProducer` publica no SNS usando `SnsAsyncClient` diretamente (não via Spring Cloud AWS). O tópico é resolvido em tempo de execução buscando o ARN via `listTopics()`.

Cada publicação inclui um **message attribute** chamado `tipo`, que é a chave usada pelo FilterPolicy para rotear a mensagem para a fila correta:

```java
snsClient.publish(PublishRequest.builder()
    .topicArn(topicArn)
    .message(payload)           // JSON de PedidoMessage
    .messageAttributes(Map.of(
        "tipo", MessageAttributeValue.builder()
            .dataType("String")
            .stringValue(pedido.tipo().name())  // "NOVO" ou "CANCELAMENTO"
            .build()))
    .build());
```

### Infraestrutura criada no startup

O `SqsQueueInitializer` executa ao iniciar a aplicação e cria toda a infraestrutura programaticamente, na seguinte ordem:

1. Cria as filas SQS (`pedidos-novos` e `pedidos-cancelados`)
2. Obtém os ARNs das filas
3. Cria o tópico SNS (`pedidos-topic`)
4. Configura uma IAM policy em cada fila permitindo que o SNS publique nela
5. Cria as assinaturas SQS no tópico com FilterPolicy por `tipo`:
   - `pedidos-novos` → `{"tipo": ["NOVO"]}`
   - `pedidos-cancelados` → `{"tipo": ["CANCELAMENTO"]}`

### Envelope SNS → SQS

Quando o SNS entrega uma mensagem a uma fila SQS, ele **envolve o payload original em um envelope JSON**:

```json
{
  "Type": "Notification",
  "MessageId": "...",
  "TopicArn": "arn:aws:sns:...",
  "Message": "{\"pedidoId\":1,\"produto\":\"Laptop\",...}",
  "Timestamp": "...",
  "MessageAttributes": { ... }
}
```

O campo `Message` contém o JSON original como **string escapada**. Por isso os consumers não podem desserializar diretamente o body da mensagem SQS como `PedidoMessage`.

O `SnsEnvelopeExtractor` resolve isso: lê o `body` da mensagem SQS, faz parse do envelope e extrai o campo `Message` antes de desserializar para `PedidoMessage`.

## Limitação conhecida — Floci e SNS FilterPolicy

O Floci **não implementa SNS FilterPolicy**. Ao publicar uma mensagem no tópico, ele entrega para **todas as filas inscritas**, ignorando silenciosamente os filtros configurados por atributo de mensagem.

**Comportamento observado em ambiente local:**
- `NovoPedidoConsumer` recebe pedidos `NOVO` e `CANCELAMENTO`
- `CancelamentoPedidoConsumer` recebe pedidos `NOVO` e `CANCELAMENTO`

**Em produção (AWS real) o roteamento funciona corretamente**, pois o SNS aplica o FilterPolicy e cada fila recebe apenas as mensagens cujo atributo `tipo` corresponde ao filtro configurado.

**Alternativas para ambiente local com FilterPolicy funcional:**
- Substituir o Floci pelo [LocalStack](https://localstack.cloud/) — suporte completo a SNS FilterPolicy
- Implementar filtro no consumer, verificando o `tipo` da mensagem e descartando o que não corresponde

## Estrutura do projeto

```
src/main/java/io/floci/example/
├── config/
│   ├── SqsConfig.java            # Beans AWS (SqsAsyncClient, SnsAsyncClient, SqsTemplate)
│   └── SqsQueueInitializer.java  # Criação automática da infraestrutura no startup
├── dto/
│   ├── PedidoMessage.java        # Record com os dados do pedido
│   └── TipoPedido.java           # Enum: NOVO | CANCELAMENTO
├── producer/
│   ├── PedidoController.java     # REST endpoint POST /pedidos
│   └── PedidoProducer.java       # Publicação no tópico SNS
└── consumer/
    ├── NovoPedidoConsumer.java            # Listener da fila pedidos-novos
    ├── CancelamentoPedidoConsumer.java    # Listener da fila pedidos-cancelados
    └── SnsEnvelopeExtractor.java         # Desempacota o envelope SNS
```
