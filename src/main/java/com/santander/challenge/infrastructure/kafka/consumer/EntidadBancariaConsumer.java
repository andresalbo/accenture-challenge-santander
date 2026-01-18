package com.santander.challenge.infrastructure.kafka.consumer;

import java.util.UUID;

import com.santander.challenge.application.service.EntidadBancariaApplicationService;
import com.santander.challenge.application.service.IdempotencyApplicationService;
import com.santander.challenge.domain.model.EntidadBancariaDomain;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import com.santander.challenge.infrastructure.kafka.config.KafkaConfig;
import com.santander.challenge.infrastructure.kafka.dto.EntidadBancariaMessage;
import com.santander.challenge.infrastructure.kafka.dto.EntidadBancariaMessage.MessageStatus;
import com.santander.challenge.infrastructure.kafka.producer.EntidadBancariaProducer;
import com.santander.challenge.infrastructure.kafka.service.MessageTrackingService;

/**
 * Consumer de Kafka que procesa las solicitudes de creación de EntidadBancaria.
 * Implementa la lógica de idempotencia y persiste las entidades en la base de datos.
 */
@Service
public class EntidadBancariaConsumer {

    private static final Logger logger = LoggerFactory.getLogger(EntidadBancariaConsumer.class);

    private final EntidadBancariaApplicationService entidadBancariaService;
    private final IdempotencyApplicationService idempotencyService;
    private final EntidadBancariaProducer producer;
    private final MessageTrackingService trackingService;

    public EntidadBancariaConsumer(
            EntidadBancariaApplicationService entidadBancariaService,
            IdempotencyApplicationService idempotencyService,
            EntidadBancariaProducer producer,
            MessageTrackingService trackingService) {
        this.entidadBancariaService = entidadBancariaService;
        this.idempotencyService = idempotencyService;
        this.producer = producer;
        this.trackingService = trackingService;
    }

    /**
     * Listener que procesa mensajes del topic de creación de entidades.
     * 
     * @param message El mensaje con los datos de la entidad
     * @param partition La partición de donde viene el mensaje
     * @param offset El offset del mensaje
     * @param acknowledgment Para confirmar el procesamiento manualmente
     */
    @KafkaListener(
        topics = KafkaConfig.TOPIC_ENTIDAD_BANCARIA_CREATE,
        groupId = KafkaConfig.GROUP_ID,
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void processCreateRequest(
            @Payload EntidadBancariaMessage message,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        logger.info("Mensaje recibido - messageId: {}, partition: {}, offset: {}", 
                message.getMessageId(), partition, offset);

        // Actualizar estado a PROCESSING
        message.setStatus(MessageStatus.PROCESSING);
        trackingService.updateStatus(message);

        try {
            // Verificar idempotencia
            String idempotencyKey = message.getIdempotencyKey();
            
            // checkAndSaveKey retorna false si ya existe (duplicado)
            boolean isNewRequest = idempotencyService.checkAndSaveKey(idempotencyKey);

            if (!isNewRequest) {
                // La entidad ya existe - es un duplicado
                logger.warn("Solicitud duplicada detectada - idempotencyKey: {}", idempotencyKey);
                message.setStatus(MessageStatus.DUPLICATE);
                message.setErrorMessage("Request already processed with this idempotency key");
                trackingService.updateStatus(message);
                producer.sendResult(message);
                acknowledgment.acknowledge();
                return;
            }

            // Crear la entidad (sin establecer ID, se auto-genera)
            EntidadBancariaDomain entidad = new EntidadBancariaDomain(
                message.getNombre(),
                message.getCodigoBcra(),
                message.getPais()
            );

            // Guardar en base de datos
            EntidadBancariaDomain savedEntity = entidadBancariaService.crear(entidad);
            
            logger.info("Entidad creada exitosamente - id: {}, nombre: {}", 
                    savedEntity.getId(), savedEntity.getNombre());

            // Actualizar mensaje con resultado exitoso
            message.setStatus(MessageStatus.COMPLETED);
            message.setEntityId(savedEntity.getId());
            trackingService.updateStatus(message);

            // Enviar resultado al topic de resultados
            producer.sendResult(message);

            // Confirmar el procesamiento del mensaje
            acknowledgment.acknowledge();

        } catch (Exception e) {
            logger.error("Error procesando mensaje - messageId: {}", message.getMessageId(), e);
            
            // Actualizar estado a FAILED
            message.setStatus(MessageStatus.FAILED);
            message.setErrorMessage(e.getMessage());
            trackingService.updateStatus(message);
            
            // Enviar resultado de error
            producer.sendResult(message);
            
            // Confirmar el mensaje para evitar reprocesamiento infinito
            // En producción, podrías querer implementar retry o DLQ
            acknowledgment.acknowledge();
        }
    }
}
