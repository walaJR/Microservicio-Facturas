package com.tienda.microservicio.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tienda.microservicio.service.ConsumirMensajeService;

@RestController
@RequestMapping("/rabbitmq")
public class MensajeController {

    private final ConsumirMensajeService consumirMensajeService;

    public MensajeController(ConsumirMensajeService consumirMensajeService) {

        this.consumirMensajeService = consumirMensajeService;
    }

    // Consumimos manualmente mensaje de la cola de RabbitMQ
    @GetMapping("/ultimoMensaje")
    public ResponseEntity<String> obtenerUltimoMensaje() {

        String message = consumirMensajeService.obtenerUltimoMensaje();

        if (null == message) {
            return ResponseEntity.noContent().build();
        } else {
            return ResponseEntity.ok(message);
        }
    }

}
