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
