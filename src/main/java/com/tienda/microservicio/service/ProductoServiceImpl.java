package com.tienda.microservicio.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.tienda.microservicio.model.Producto;
import com.tienda.microservicio.repository.ProductoRepository;

import lombok.RequiredArgsConstructor;

//List<Producto> getAllProducts();

//Producto createProduct(Producto producto);

@Service
@RequiredArgsConstructor
public class ProductoServiceImpl implements ProductoService {

    private final ProductoRepository productoRepository;

    @Override
    public List<Producto> getAllProducts() {
        return productoRepository.findAll();
    }

    @Override
    public Producto createProduct(Producto producto) {
        return productoRepository.save(producto);
    }

}
