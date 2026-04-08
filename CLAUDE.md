# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Spring Boot 3.4.4 application demonstrating event-driven order processing using AWS SNS/SQS with **Floci** (local AWS mock at port 4566). Language: Java 21. Build: Maven.

## Commands

```bash
# Start local infrastructure (must run before the app)
docker-compose up -d

# Run application
mvn spring-boot:run

# Build
mvn clean package

# Run all tests
mvn test

# Run a specific test class
mvn test -Dtest=ClassName

# Run a specific test method
mvn test -Dtest=ClassName#methodName
```

## Architecture

The application implements an SNS fan-out pattern with SQS filter-based routing for order events (`PedidoMessage`).

**Flow:**
```
POST /pedidos
  → PedidoController
  → PedidoProducer (publishes to SNS with attribute "tipo")
  → pedidos-topic (SNS)
      ├── FilterPolicy tipo=NOVO        → pedidos-novos (SQS) → NovoPedidoConsumer
      └── FilterPolicy tipo=CANCELAMENTO → pedidos-cancelados (SQS) → CancelamentoPedidoConsumer
```

**Key components:**
- `SqsQueueInitializer` — runs at startup; creates the SNS topic, SQS queues, subscriptions, filter policies, and IAM policies programmatically. No manual AWS setup required.
- `SqsConfig` — configures `SqsAsyncClient`, `SnsAsyncClient`, `SqsTemplate`, and `SqsMessageListenerContainerFactory` pointing to Floci at `localhost:4566`.
- `SnsEnvelopeExtractor` — consumers receive SNS-wrapped messages; this utility unwraps the envelope and deserializes the inner `PedidoMessage`.
- `PedidoMessage` — Java record: `(Long pedidoId, String produto, Integer quantidade, Double valorTotal, TipoPedido tipo)`.
- `TipoPedido` — enum: `NOVO`, `CANCELAMENTO`.

**AWS credentials in dev:** hardcoded to `access-key: test / secret-key: test` against `http://localhost:4566`.

## Configuration

Key properties in `application.yml`:
```yaml
aws.endpoint-override: http://localhost:4566
aws.region.static: us-east-1
app.sns.topic-name: pedidos-topic
app.sqs.queue-novos: pedidos-novos
app.sqs.queue-cancelados: pedidos-cancelados
```

## Testing the API

```bash
curl -X POST http://localhost:8080/pedidos \
  -H "Content-Type: application/json" \
  -d '{"pedidoId": 1, "produto": "Laptop", "quantidade": 2, "valorTotal": 5000.00, "tipo": "NOVO"}'
```
