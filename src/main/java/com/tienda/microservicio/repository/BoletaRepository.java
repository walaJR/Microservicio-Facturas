package com.tienda.microservicio.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.tienda.microservicio.model.Boleta;

@Repository
public interface BoletaRepository extends JpaRepository<Boleta, Long> {

}
