package com.tienda.microservicio.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class RenameResponse {
    private String message;
    private String oldFileName;
    private String newFileName;
    private String newFilePath;
    private boolean success;
}