package com.tienda.microservicio.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.tienda.microservicio.config.RabbitMQConfig;
import com.tienda.microservicio.model.Boleta;
import com.tienda.microservicio.model.DetalleBoleta;
import com.tienda.microservicio.model.Producto;
import com.tienda.microservicio.model.ProductoCompra;
import com.tienda.microservicio.repository.BoletaRepository;
import com.tienda.microservicio.repository.ProductoRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CompraServiceImpl implements CompraService {

    private final RabbitTemplate rabbitTemplate;

    @Autowired
    private ProductoRepository productoRepo;

    @Autowired
    private BoletaRepository boletaRepo;

    @Autowired
    private PDFService pdfService;

    @Override
    public Boleta procesarCompra(List<ProductoCompra> carrito) {

        // Creamos la boleta
        Boleta boleta = new Boleta();
        boleta.setFecha(LocalDateTime.now()); // Le ponemos fecha
        List<DetalleBoleta> detalles = new ArrayList<>(); // Añadimos los detalles de cada item del carrito

        double total = 0;
        for (ProductoCompra item : carrito) {
            Producto producto = productoRepo.findById(item.getIdProducto())
                    .orElseThrow(() -> new RuntimeException("Producto no encontrado"));

            DetalleBoleta detalle = new DetalleBoleta();
            detalle.setProducto(producto);
            detalle.setCantidad(item.getCantidad());
            detalle.setPrecioUnitario(producto.getPrecio());
            detalle.setBoleta(boleta);

            detalles.add(detalle);
            total += producto.getPrecio() * item.getCantidad();
        }

        boleta.setDetalles(detalles);
        boleta.setTotal(total);

        // Solo enviamos a la cola de RabbitMQ, NO guardamos en base de datos todavía
        rabbitTemplate.convertAndSend(RabbitMQConfig.MAIN_EXCHANGE, RabbitMQConfig.ROUTING_KEY, boleta);

        // Retornamos la boleta sin guardar en BD
        return boleta;
    }

    // Método para guardar la boleta en base de datos, se llamará al método cuando
    // se consuma el mensaje de RabbitMQ
    public Boleta guardarBoletaEnBD(Boleta boleta) {
        return boletaRepo.save(boleta);
    }

    // Generamos la boleta en formato PDF
    @Override
    public byte[] generarPDFBoleta(Long boletaId) {
        Boleta boleta = boletaRepo.findById(boletaId)
                .orElseThrow(() -> new RuntimeException("Boleta no encontrada"));
        return pdfService.generarBoletaPDF(boleta);
    }
}