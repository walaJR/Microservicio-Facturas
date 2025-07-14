package com.tienda.microservicio.service;

import org.springframework.stereotype.Service;
import java.io.IOException;
import org.springframework.amqp.core.Message;
import com.rabbitmq.client.Channel;

@Service
public interface ConsumirMensajeService {

    String obtenerUltimoMensaje();

    void recibirMensaje(Object mensaje);

    void recibirMensajeConAckManual(Message mensaje, Channel canal) throws IOException;

}
