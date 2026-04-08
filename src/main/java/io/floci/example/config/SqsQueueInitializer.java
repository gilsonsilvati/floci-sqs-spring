package io.floci.example.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.services.sns.SnsAsyncClient;
import software.amazon.awssdk.services.sns.model.CreateTopicRequest;
import software.amazon.awssdk.services.sns.model.SubscribeRequest;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.CreateQueueRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueAttributesRequest;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;
import software.amazon.awssdk.services.sqs.model.SetQueueAttributesRequest;

import java.util.Map;

/**
 * Cria a infraestrutura AWS ao iniciar a aplicação:
 *   SNS topic: pedidos-topic
 *   SQS:       pedidos-novos       → recebe mensagens com tipo=NOVO
 *   SQS:       pedidos-cancelados  → recebe mensagens com tipo=CANCELAMENTO
 *
 * Cada fila é subscrita ao topic com um filter policy baseado no
 * message attribute "tipo", garantindo roteamento automático pelo SNS.
 */
@Configuration
public class SqsQueueInitializer {

    private static final Logger log = LoggerFactory.getLogger(SqsQueueInitializer.class);

    @Value("${app.sns.topic-name}")
    private String topicName;

    @Value("${app.sqs.queue-novos}")
    private String queueNovos;

    @Value("${app.sqs.queue-cancelados}")
    private String queueCancelados;

    @Bean
    public ApplicationRunner inicializarInfraestrutura(SqsAsyncClient sqsClient,
                                                        SnsAsyncClient snsClient) {
        return args -> {
            // 1. Criar filas SQS
            String urlNovos       = criarFila(sqsClient, queueNovos);
            String urlCancelados  = criarFila(sqsClient, queueCancelados);

            // 2. Obter ARNs das filas
            String arnNovos      = obterArn(sqsClient, urlNovos);
            String arnCancelados = obterArn(sqsClient, urlCancelados);

            // 3. Criar tópico SNS
            String topicArn = snsClient
                    .createTopic(CreateTopicRequest.builder().name(topicName).build())
                    .join()
                    .topicArn();
            log.info("Tópico SNS pronto: {}", topicArn);

            // 4. Permitir que o SNS publique em cada fila
            configurarPoliticaSqs(sqsClient, urlNovos, arnNovos, topicArn);
            configurarPoliticaSqs(sqsClient, urlCancelados, arnCancelados, topicArn);

            // 5. Subscrever filas ao topic com filter policies
            subscrever(snsClient, topicArn, arnNovos,      "{\"tipo\":[\"NOVO\"]}");
            subscrever(snsClient, topicArn, arnCancelados, "{\"tipo\":[\"CANCELAMENTO\"]}");
        };
    }

    private String criarFila(SqsAsyncClient sqsClient, String nome) {
        String url = sqsClient
                .createQueue(CreateQueueRequest.builder().queueName(nome).build())
                .join()
                .queueUrl();
        log.info("Fila SQS pronta: {}", url);
        return url;
    }

    private String obterArn(SqsAsyncClient sqsClient, String queueUrl) {
        return sqsClient
                .getQueueAttributes(GetQueueAttributesRequest.builder()
                        .queueUrl(queueUrl)
                        .attributeNames(QueueAttributeName.QUEUE_ARN)
                        .build())
                .join()
                .attributes()
                .get(QueueAttributeName.QUEUE_ARN);
    }

    private void configurarPoliticaSqs(SqsAsyncClient sqsClient,
                                        String queueUrl,
                                        String queueArn,
                                        String topicArn) {
        String policy = """
                {
                  "Version": "2012-10-17",
                  "Statement": [{
                    "Effect": "Allow",
                    "Principal": "*",
                    "Action": "sqs:SendMessage",
                    "Resource": "%s",
                    "Condition": {
                      "ArnEquals": { "aws:SourceArn": "%s" }
                    }
                  }]
                }
                """.formatted(queueArn, topicArn);

        sqsClient.setQueueAttributes(SetQueueAttributesRequest.builder()
                .queueUrl(queueUrl)
                .attributes(Map.of(QueueAttributeName.POLICY, policy))
                .build()).join();
    }

    private void subscrever(SnsAsyncClient snsClient,
                             String topicArn,
                             String queueArn,
                             String filterPolicy) {
        snsClient.subscribe(SubscribeRequest.builder()
                .topicArn(topicArn)
                .protocol("sqs")
                .endpoint(queueArn)
                .attributes(Map.of("FilterPolicy", filterPolicy))
                .build()).join();
        log.info("Fila {} subscrita ao topic com filtro: {}", queueArn, filterPolicy);
    }
}
