package com.tienda.microservicio.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.tienda.microservicio.dto.S3FileInfo;
import com.tienda.microservicio.dto.UploadResponse;
import com.tienda.microservicio.service.AwsS3Service;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/s3")
@RequiredArgsConstructor
public class AwsS3Controller {

    private final AwsS3Service awsS3Service;

    // Endpoint para subir un archivo a Bucket S3 de AWS
    // POST /api/s3/upload
    @PostMapping("/upload")
    public ResponseEntity<UploadResponse> uploadFile(@RequestParam("file") MultipartFile file) {

        // Validaciones
        if (file.isEmpty()) {
            UploadResponse errorResponse = new UploadResponse(
                    "No se ha seleccionado ningún archivo",
                    null, null, 0, false);
            return ResponseEntity.badRequest().body(errorResponse);
        }

        // Validamos el tamaño del archivo (Máximo 50MB)
        if (file.getSize() > 50 * 1024 * 1024) {
            UploadResponse errorResponse = new UploadResponse(
                    "El archivo es demasiado grande. Máximo 50MB permitido",
                    null, null, 0, false);
            return ResponseEntity.badRequest().body(errorResponse);
        }

        try {
            UploadResponse response = awsS3Service.uploadFile(file);

            if (response.isSuccess()) {
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
            }

        } catch (Exception e) {
            UploadResponse errorResponse = new UploadResponse(
                    "Error interno del servidor: " + e.getMessage(),
                    null, null, 0, false);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    // Endpoint para actualizar/sobrescribir un archivo existente en Bucket S3 de
    // AWS
    // PUT /api/s3/files/{fileName}
    @PutMapping("/files/{fileName}")
    public ResponseEntity<UploadResponse> updateFile(
            @PathVariable String fileName,
            @RequestParam("file") MultipartFile file) {

        // Validaciones
        if (file.isEmpty()) {
            UploadResponse errorResponse = new UploadResponse(
                    "No se ha seleccionado ningún archivo",
                    null, null, 0, false);
            return ResponseEntity.badRequest().body(errorResponse);
        }

        // Validamos el tamaño del archivo (Máximo 50MB)
        if (file.getSize() > 50 * 1024 * 1024) {
            UploadResponse errorResponse = new UploadResponse(
                    "El archivo es demasiado grande. Máximo 50MB permitido",
                    null, null, 0, false);
            return ResponseEntity.badRequest().body(errorResponse);
        }

        try {
            // Verificamos si el archivo existe
            if (!awsS3Service.fileExists(fileName)) {
                UploadResponse errorResponse = new UploadResponse(
                        "El archivo especificado no existe: " + fileName,
                        null, null, 0, false);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
            }

            // Actualizamos el archivo
            UploadResponse response = awsS3Service.updateFile(fileName, file);

            if (response.isSuccess()) {
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
            }

        } catch (Exception e) {
            UploadResponse errorResponse = new UploadResponse(
                    "Error interno del servidor: " + e.getMessage(),
                    null, null, 0, false);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    // Endpoint para listar todos los archivos de Bucket S3 de AWS
    // GET /api/s3/files
    @GetMapping("/files")
    public ResponseEntity<List<S3FileInfo>> listFiles() {
        try {
            List<S3FileInfo> files = awsS3Service.listFiles();
            return ResponseEntity.ok(files);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // Endpoint para descargar un archivo de Bucket S3 de AWS
    // GET /api/s3/download/{fileName}
    @GetMapping("/download/{fileName}")
    public ResponseEntity<byte[]> downloadFile(@PathVariable String fileName) {
        try {
            // Verificamos si el archivo existe
            if (!awsS3Service.fileExists(fileName)) {
                return ResponseEntity.notFound().build();
            }

            // Descargamos el archivo
            byte[] fileContent = awsS3Service.downloadFile(fileName);

            // Obtenemos información del archivo para headers
            S3FileInfo fileInfo = awsS3Service.getFileInfo(fileName);

            // Configuramos headers de respuesta
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            headers.setContentDispositionFormData("attachment", fileInfo.getFileName());
            headers.setContentLength(fileContent.length);

            return ResponseEntity.ok()
                    .headers(headers)
                    .body(fileContent);

        } catch (RuntimeException e) {
            if (e.getMessage().contains("no encontrado")) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // Endpoint para eliminar un archivo de Bucket S3 de AWS
    // DELETE /api/s3/files/{fileName}
    @DeleteMapping("/files/{fileName}")
    public ResponseEntity<Map<String, Object>> deleteFile(@PathVariable String fileName) {
        Map<String, Object> response = new HashMap<>();

        try {
            // Verificamos si el archivo existe
            if (!awsS3Service.fileExists(fileName)) {
                response.put("success", false);
                response.put("message", "Archivo no encontrado");
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
            }

            // Eliminamos el archivo
            boolean deleted = awsS3Service.deleteFile(fileName);

            if (deleted) {
                response.put("success", true);
                response.put("message", "Archivo eliminado exitosamente");
                response.put("fileName", fileName);
                return ResponseEntity.ok(response);
            } else {
                response.put("success", false);
                response.put("message", "Error al eliminar el archivo");
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
            }

        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Error interno del servidor: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    // Endpoint para obtener información de un archivo específico de Bucket S3 de
    // AWS
    // GET /api/s3/files/{fileName}/info
    @GetMapping("/files/{fileName}/info")
    public ResponseEntity<S3FileInfo> getFileInfo(@PathVariable String fileName) {
        try {
            S3FileInfo fileInfo = awsS3Service.getFileInfo(fileName);
            return ResponseEntity.ok(fileInfo);
        } catch (RuntimeException e) {
            if (e.getMessage().contains("no encontrado")) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // Endpoint para verificar si un archivo existe de Bucket S3 de AWS
    // GET /api/s3/files/{fileName}/exists
    @GetMapping("/files/{fileName}/exists")
    public ResponseEntity<Map<String, Object>> checkFileExists(@PathVariable String fileName) {
        Map<String, Object> response = new HashMap<>();

        try {
            boolean exists = awsS3Service.fileExists(fileName);
            response.put("exists", exists);
            response.put("fileName", fileName);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("exists", false);
            response.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    // Endpoint de salud para verificar conectividad con S3 de Bucket S3 de AWS
    // GET /api/s3/health
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        Map<String, Object> response = new HashMap<>();

        try {
            // Intentamos listar archivos como test de conectividad
            awsS3Service.listFiles();

            response.put("status", "UP");
            response.put("service", "AWS S3");
            response.put("message", "Conexión exitosa con S3");

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("status", "DOWN");
            response.put("service", "AWS S3");
            response.put("message", "Error de conexión con S3: " + e.getMessage());

            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
        }
    }
}