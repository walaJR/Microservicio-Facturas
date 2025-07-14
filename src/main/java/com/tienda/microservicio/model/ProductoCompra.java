package com.tienda.microservicio.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data

public class ProductoCompra {
    private Long idProducto;
    private int cantidad;

}
