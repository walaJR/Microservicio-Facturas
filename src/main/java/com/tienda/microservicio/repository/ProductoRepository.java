package com.tienda.microservicio.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.tienda.microservicio.model.Producto;

@Repository
public interface ProductoRepository extends JpaRepository<Producto, Long> {

}