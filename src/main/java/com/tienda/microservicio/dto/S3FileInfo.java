package com.tienda.microservicio.dto;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class S3FileInfo {
    private String fileName;
    private String key;
    private long size;
    private Instant lastModified;
    private String contentType;
    private String url;
}