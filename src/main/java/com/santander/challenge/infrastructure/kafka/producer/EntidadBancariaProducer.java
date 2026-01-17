package com.santander.challenge.infrastructure.kafka.producer;

import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import com.santander.challenge.infrastructure.kafka.config.KafkaConfig;
import com.santander.challenge.infrastructure.kafka.dto.EntidadBancariaMessage;

/**
 * Producer de Kafka para enviar solicitudes de creación de EntidadBancaria.
 * Usa el idempotencyKey como partition key para garantizar orden por entidad.
 */
@Service
public class EntidadBancariaProducer {

    private static final Logger logger = LoggerFactory.getLogger(EntidadBancariaProducer.class);

    private final KafkaTemplate<String, EntidadBancariaMessage> kafkaTemplate;

    public EntidadBancariaProducer(KafkaTemplate<String, EntidadBancariaMessage> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * Envía un mensaje de creación de entidad al topic de Kafka.
     * 
     * @param message El mensaje con los datos de la entidad a crear
     * @return CompletableFuture con el resultado del envío
     */
    public CompletableFuture<SendResult<String, EntidadBancariaMessage>> sendCreateRequest(EntidadBancariaMessage message) {
        logger.info("Enviando mensaje a Kafka - messageId: {}, idempotencyKey: {}", 
                message.getMessageId(), message.getIdempotencyKey());

        // Usar idempotencyKey como partition key para garantizar que mensajes
        // con el mismo key vayan a la misma partición (orden garantizado)
        CompletableFuture<SendResult<String, EntidadBancariaMessage>> future = 
                kafkaTemplate.send(
                    KafkaConfig.TOPIC_ENTIDAD_BANCARIA_CREATE,
                    message.getIdempotencyKey(),
                    message
                );

        future.whenComplete((result, ex) -> {
            if (ex == null) {
                logger.info("Mensaje enviado exitosamente - topic: {}, partition: {}, offset: {}",
                        result.getRecordMetadata().topic(),
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            } else {
                logger.error("Error enviando mensaje a Kafka - messageId: {}", message.getMessageId(), ex);
            }
        });

        return future;
    }

    /**
     * Envía un mensaje de resultado al topic de resultados.
     * 
     * @param message El mensaje con el resultado del procesamiento
     */
    public void sendResult(EntidadBancariaMessage message) {
        logger.info("Enviando resultado a Kafka - messageId: {}, status: {}", 
                message.getMessageId(), message.getStatus());

        kafkaTemplate.send(
            KafkaConfig.TOPIC_ENTIDAD_BANCARIA_RESULT,
            message.getIdempotencyKey(),
            message
        ).whenComplete((result, ex) -> {
            if (ex == null) {
                logger.debug("Resultado enviado exitosamente - messageId: {}", message.getMessageId());
            } else {
                logger.error("Error enviando resultado a Kafka - messageId: {}", message.getMessageId(), ex);
            }
        });
    }
}
