package com.tienda.microservicio.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UploadResponse {
    private String message;
    private String fileName;
    private String fileUrl;
    private long fileSize;
    private boolean success;
}