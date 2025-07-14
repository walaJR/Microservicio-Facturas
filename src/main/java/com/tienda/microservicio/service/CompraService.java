package com.tienda.microservicio.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.tienda.microservicio.model.Boleta;
import com.tienda.microservicio.model.ProductoCompra;

@Service
public interface CompraService {

    Boleta procesarCompra(List<ProductoCompra> carrito);

    Boleta guardarBoletaEnBD(Boleta boleta);

    byte[] generarPDFBoleta(Long boletaId);
}