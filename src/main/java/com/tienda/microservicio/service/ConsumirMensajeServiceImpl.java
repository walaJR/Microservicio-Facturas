package com.tienda.microservicio.service;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import com.tienda.microservicio.model.Boleta;
import com.tienda.microservicio.dto.UploadResponse;

import org.springframework.amqp.core.Message;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.GetResponse;
import com.tienda.microservicio.config.RabbitMQConfig;

@Service
public class ConsumirMensajeServiceImpl implements ConsumirMensajeService {

    @Autowired
    private CompraServiceImpl compraService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PDFService pdfService;

    @Autowired
    private AwsS3Service awsS3Service;

    // Consumimos mensajes MANUALMENTE de la cola RabbitMQ
    @Override
    public String obtenerUltimoMensaje() {
        String mensaje = null;
        ConnectionFactory factory = new ConnectionFactory(); // Generamos la conexión a RabbitMQ

        factory.setHost("54.85.59.177");
        factory.setUsername("hola1");
        factory.setPassword("hola1");

        try (Connection conexion = factory.newConnection(); Channel canal = conexion.createChannel()) {

            // Manejamos manualmente el ack/nack
            GetResponse response = canal.basicGet("myQueue", false);

            if (response != null) {
                mensaje = new String(response.getBody(), "UTF-8");
                System.out.println("Mensaje recibido: " + mensaje);

                try {
                    // Identificamos y procesam el tipo de mensaje
                    String resultadoProcesamiento = procesarMensajeSegunTipo(mensaje);

                    // Si el mensaje se procesó exitosamente, hace ACK y manda output de retorno
                    // exitoso
                    if (resultadoProcesamiento != null) {
                        canal.basicAck(response.getEnvelope().getDeliveryTag(), false);
                        mensaje = resultadoProcesamiento;
                        System.out.println("Mensaje procesado exitosamente y ACK enviado");
                    }

                } catch (Exception e) {
                    System.err.println("Error al procesar mensaje: " + e.getMessage());
                    // Si hay error en procesamiento, hace NACK y enviamos a DLQ
                    canal.basicNack(response.getEnvelope().getDeliveryTag(), false, false);
                    mensaje = "Error al procesar mensaje (enviado a DLQ): " + e.getMessage();
                    System.out.println("Mensaje enviado a DLQ debido a error");
                }

            } else {
                System.out.println("No hay mensajes en la cola");
            }

        } catch (Exception e) {
            System.out.println("Error al consumir el mensaje desde RabbitMQ");
            e.printStackTrace();
        }
        return mensaje;
    }

    // Método para identificar el tipo de mensaje y procesarlo según se requiera
    private String procesarMensajeSegunTipo(String mensajeJson) throws Exception {
        // Primero intentamos identificar si es una Boleta
        if (esBoleta(mensajeJson)) {
            return procesarBoletaConValidaciones(mensajeJson);
        }

        // Si no es ningún tipo conocido, solo retornamos el mensaje original
        System.out.println("Mensaje no reconocido como tipo procesable: " + mensajeJson);
        return "Mensaje recibido (no procesado): " + mensajeJson;
    }

    // Método para verificar si el mensaje JSON corresponde a una Boleta
    private boolean esBoleta(String mensajeJson) {
        try {
            // Intentamos parsear como JsonNode para verificar campos específicos de Boleta
            var jsonNode = objectMapper.readTree(mensajeJson);

            // Verificamos si tiene los campos característicos de una Boleta
            return jsonNode.has("fecha") &&
                    jsonNode.has("total") &&
                    jsonNode.has("detalles");

        } catch (Exception e) {
            return false;
        }
    }

    // Método para procesar específicamente mensajes de tipo Boleta
    private String procesarBoleta(String mensajeJson) {
        try {
            // Convertimos el mensaje JSON a objeto Boleta
            Boleta boleta = objectMapper.readValue(mensajeJson, Boleta.class);

            // Guardamos la boleta en la base de datos
            Boleta boletaGuardada = compraService.guardarBoletaEnBD(boleta);

            System.out.println("Boleta guardada en BD con ID: " + boletaGuardada.getId());

            return "Boleta procesada y guardada en BD con ID: " + boletaGuardada.getId();

        } catch (Exception e) {
            System.err.println("Error al procesar la boleta desde RabbitMQ: " + e.getMessage());
            e.printStackTrace();
            return "Error al procesar la boleta: " + e.getMessage();
        }
    }

    // @RabbitListener(queues = RabbitMQConfig.MAIN_QUEUE)
    @Override
    public void recibirMensaje(Object objeto) {
        System.out.println("Mensaje recibido en myQueue: " + objeto);
    }

    // @RabbitListener(id = "listener-myQueue-v2", queues =
    // RabbitMQConfig.MAIN_QUEUE, ackMode = "MANUAL")
    public void recibirMensajeConAckManualV2(String mensajeJson, Channel canal,
            @Header("amqp_deliveryTag") long deliveryTag) throws IOException {

        try {
            System.out.println("Mensaje recibido con ACK manual V2: " + mensajeJson);

            // Se valida que el mensaje no esté vacío
            if (mensajeJson == null || mensajeJson.trim().isEmpty()) {
                throw new RuntimeException("Mensaje vacío recibido");
            }

            // Procesamos el mensaje
            String resultadoProcesamiento = procesarMensajeConValidaciones(mensajeJson);

            System.out.println("Resultado del procesamiento V2: " + resultadoProcesamiento);
            Thread.sleep(2000);

            // ACK exitoso
            canal.basicAck(deliveryTag, false);
            System.out.println("Acknowledge OK enviado V2 - Mensaje procesado exitosamente");

        } catch (Exception e) {
            System.err.println("Error en procesamiento con ACK manual V2: " + e.getMessage());
            e.printStackTrace();

            try {
                // NACK y enviarmos a DLQ
                canal.basicNack(deliveryTag, false, false);
                System.out.println("Acknowledge NO OK enviado V2 - Mensaje enviado a DLQ");
            } catch (IOException ioException) {
                System.err.println("Error al enviar NACK V2: " + ioException.getMessage());
                ioException.printStackTrace();
            }
        }
    }

    // @RabbitListener(id = "listener-myQueue", queues = RabbitMQConfig.MAIN_QUEUE,
    // ackMode = "MANUAL")
    @Override
    public void recibirMensajeConAckManual(Message mensaje, Channel canal) throws IOException {

        try {

            String body = new String(mensaje.getBody());
            System.out.println("Mensaje recibido: " + body);

            if (body.contains("error")) {
                throw new RuntimeException("Error forzado para probar la DLQ");
            }

            Thread.sleep(5000);

            canal.basicAck(mensaje.getMessageProperties().getDeliveryTag(), false);
            System.out.println("Acknowledge OK enviado");
        } catch (Exception e) {
            canal.basicNack(mensaje.getMessageProperties().getDeliveryTag(), false, false);
            System.out.println("Acknowledge NO OK enviado");
        }
    }

    // Procesa mensajes con validaciones específicas
    private String procesarMensajeConValidaciones(String mensajeJson) throws Exception {

        // Identificamos y procesamos el tipo de mensaje
        if (esBoleta(mensajeJson)) {
            return procesarBoletaConValidaciones(mensajeJson);
        }

        // Si no es ningún tipo conocido, solo retornamos el mensaje original
        System.out.println("Mensaje no reconocido como tipo procesable: " + mensajeJson);
        return "Mensaje recibido (no procesado): " + mensajeJson;
    }

    // Método para procesar específicamente mensajes de tipo Boleta. Además la
    // subimos a la BD y al Bucket S3
    private String procesarBoletaConValidaciones(String mensajeJson) throws Exception {

        // Convertimos el mensaje JSON a objeto Boleta
        Boleta boleta = objectMapper.readValue(mensajeJson, Boleta.class);

        // Validación: Si el monto es $0, lanza excepción y envía el mensaje hacia cola
        // DLQ
        if (boleta.getTotal() == 0.0) {
            throw new RuntimeException("Boleta con monto $0 - Enviando a DLQ");
        }

        // Guardamos la boleta en la base de datos (OCI en este MS)
        Boleta boletaGuardada = compraService.guardarBoletaEnBD(boleta);

        System.out.println(
                "Boleta guardada en BD con ID: " + boletaGuardada.getId() + " - Monto: $" + boletaGuardada.getTotal());

        // Ocupamos nuestro método creado subirBoletaAmazonS3() para subir la boleta a
        // S3 como PDF
        String resultadoS3 = subirBoletaAmazonS3(boletaGuardada);

        return "Boleta procesada y guardada en BD con ID: " + boletaGuardada.getId() +
                " - Monto: $" + boletaGuardada.getTotal() +
                " | " + resultadoS3;
    }

    // Método para subir la boleta a Amazon S3 como archivo PDF
    private String subirBoletaAmazonS3(Boleta boleta) {
        try {
            // Generamos PDF de la boleta
            byte[] pdfBytes = pdfService.generarBoletaPDF(boleta);

            // Creamos nombre del archivo
            String nombreArchivo = "boleta_" + boleta.getId() + ".pdf";

            // Creamos un MultipartFile temporal para usar con AwsS3Service
            MultipartFile archivoTemporal = new MultipartFileFromBytes(
                    pdfBytes,
                    nombreArchivo,
                    "application/pdf");

            // Subimos archivo a S3
            UploadResponse respuestaS3 = awsS3Service.uploadFile(archivoTemporal);

            if (respuestaS3.isSuccess()) {
                System.out.println("Boleta subida exitosamente a S3: " + respuestaS3.getFileName());
                return "S3: Boleta subida exitosamente como " + respuestaS3.getFileName();
            } else {
                System.err.println("Error al subir boleta a S3: " + respuestaS3.getMessage());
                return "S3: Error al subir boleta - " + respuestaS3.getMessage();
            }

        } catch (Exception e) {
            System.err.println("Error al procesar subida a S3: " + e.getMessage());
            e.printStackTrace();
            return "S3: Error al procesar subida - " + e.getMessage();
        }
    }

    // Clase interna de apoyo para crear un MultipartFile desde bytes
    private static class MultipartFileFromBytes implements MultipartFile {
        private final byte[] bytes;
        private final String originalFilename;
        private final String contentType;

        public MultipartFileFromBytes(byte[] bytes, String originalFilename, String contentType) {
            this.bytes = bytes;
            this.originalFilename = originalFilename;
            this.contentType = contentType;
        }

        @Override
        public String getName() {
            return "file";
        }

        @Override
        public String getOriginalFilename() {
            return originalFilename;
        }

        @Override
        public String getContentType() {
            return contentType;
        }

        @Override
        public boolean isEmpty() {
            return bytes == null || bytes.length == 0;
        }

        @Override
        public long getSize() {
            return bytes.length;
        }

        @Override
        public byte[] getBytes() {
            return bytes;
        }

        @Override
        public java.io.InputStream getInputStream() {
            return new ByteArrayInputStream(bytes);
        }

        @Override
        public void transferTo(java.io.File dest) throws IOException, IllegalStateException {
            throw new UnsupportedOperationException("transferTo not supported");
        }
    }
}