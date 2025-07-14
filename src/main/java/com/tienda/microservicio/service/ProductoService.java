package com.tienda.microservicio.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.tienda.microservicio.model.Producto;

@Service
public interface ProductoService {

    List<Producto> getAllProducts();

    Producto createProduct(Producto producto);

}
