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

import com.tienda.microservicio.dto.EfsFileInfo;
import com.tienda.microservicio.dto.RenameResponse;
import com.tienda.microservicio.dto.UploadResponse;
import com.tienda.microservicio.service.AwsEfsService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/efs")
@RequiredArgsConstructor
public class AwsEfsController {

    private final AwsEfsService awsEfsService;

    // Endpoint para subir un archivo a EFS AWS
    // POST /api/efs/upload
    @PostMapping("/upload")
    public ResponseEntity<UploadResponse> uploadFile(@RequestParam("file") MultipartFile file) {

        // Validaciones
        if (file.isEmpty()) {
            UploadResponse errorResponse = new UploadResponse(
                    "No se ha seleccionado ningún archivo",
                    null, null, 0, false);
            return ResponseEntity.badRequest().body(errorResponse);
        }

        // Validar tamaño del archivo (Máximo 50MB)
        if (file.getSize() > 50 * 1024 * 1024) {
            UploadResponse errorResponse = new UploadResponse(
                    "El archivo es demasiado grande. Máximo 50MB permitido",
                    null, null, 0, false);
            return ResponseEntity.badRequest().body(errorResponse);
        }

        try {
            UploadResponse response = awsEfsService.uploadFile(file);

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

    // Renombrar un archivo en EFS AWS
    // PUT /api/efs/files/{oldFileName}/rename?newFileName=nuevoNombre.ext

    @PutMapping("/files/{oldFileName}/rename")
    public ResponseEntity<RenameResponse> renameFile(
            @PathVariable String oldFileName,
            @RequestParam String newFileName) {

        // Validaciones
        if (oldFileName == null || oldFileName.trim().isEmpty()) {
            RenameResponse errorResponse = new RenameResponse(
                    "El nombre del archivo origen no puede estar vacío",
                    null, null, null, false);
            return ResponseEntity.badRequest().body(errorResponse);
        }

        if (newFileName == null || newFileName.trim().isEmpty()) {
            RenameResponse errorResponse = new RenameResponse(
                    "El nuevo nombre del archivo no puede estar vacío",
                    oldFileName, null, null, false);
            return ResponseEntity.badRequest().body(errorResponse);
        }

        try {
            RenameResponse response = awsEfsService.renameFile(oldFileName, newFileName);

            if (response.isSuccess()) {
                return ResponseEntity.ok(response);
            } else {
                // Determinar el código de estado HTTP apropiado
                if (response.getMessage().contains("no encontrado")) {
                    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
                } else if (response.getMessage().contains("ya existe")) {
                    return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
                } else {
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
                }
            }

        } catch (Exception e) {
            RenameResponse errorResponse = new RenameResponse(
                    "Error interno del servidor: " + e.getMessage(),
                    oldFileName, newFileName, null, false);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    // Endpoint para listar todos los archivos de EFS AWS
    // GET /api/efs/files
    @GetMapping("/files")
    public ResponseEntity<List<EfsFileInfo>> listFiles() {
        try {
            List<EfsFileInfo> files = awsEfsService.listFiles();
            return ResponseEntity.ok(files);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // Endpoint para descargar un archivo de EFS AWS
    // GET /api/efs/download/{fileName}
    @GetMapping("/download/{fileName}")
    public ResponseEntity<byte[]> downloadFile(@PathVariable String fileName) {
        try {
            // Verificamos si el archivo existe
            if (!awsEfsService.fileExists(fileName)) {
                return ResponseEntity.notFound().build();
            }

            // Se descarga el archivo
            byte[] fileContent = awsEfsService.downloadFile(fileName);

            // Obtenemos información del archivo para headers
            EfsFileInfo fileInfo = awsEfsService.getFileInfo(fileName);

            // Configuramos headers de respuesta
            HttpHeaders headers = new HttpHeaders();

            // Establecemos content type si está disponible
            if (fileInfo.getContentType() != null) {
                headers.setContentType(MediaType.parseMediaType(fileInfo.getContentType()));
            } else {
                headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            }

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

    // Endpoint para eliminar un archivo de EFS AWS
    // DELETE /api/efs/files/{fileName}
    @DeleteMapping("/files/{fileName}")
    public ResponseEntity<Map<String, Object>> deleteFile(@PathVariable String fileName) {
        Map<String, Object> response = new HashMap<>();

        try {
            // Verificamos si el archivo existe
            if (!awsEfsService.fileExists(fileName)) {
                response.put("success", false);
                response.put("message", "Archivo no encontrado");
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
            }

            // Eliminamos el archivo
            boolean deleted = awsEfsService.deleteFile(fileName);

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

    // Endpoint para obtener información de un archivo específico de EFS AWS
    // GET /api/efs/files/{fileName}/info
    @GetMapping("/files/{fileName}/info")
    public ResponseEntity<EfsFileInfo> getFileInfo(@PathVariable String fileName) {
        try {
            EfsFileInfo fileInfo = awsEfsService.getFileInfo(fileName);
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

    // Endpoint para verificar si un archivo existe en EFS AWS
    // GET /api/efs/files/{fileName}/exists
    @GetMapping("/files/{fileName}/exists")
    public ResponseEntity<Map<String, Object>> checkFileExists(@PathVariable String fileName) {
        Map<String, Object> response = new HashMap<>();

        try {
            boolean exists = awsEfsService.fileExists(fileName);
            response.put("exists", exists);
            response.put("fileName", fileName);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("exists", false);
            response.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    // Endpoint de "salud" para verificar accesibilidad de EFS
    // GET /api/efs/health
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        Map<String, Object> response = new HashMap<>();

        try {
            boolean accessible = awsEfsService.isEfsAccessible();

            if (accessible) {
                response.put("status", "UP");
                response.put("service", "AWS EFS");
                response.put("message", "EFS es accesible y funcional");
                return ResponseEntity.ok(response);
            } else {
                response.put("status", "DOWN");
                response.put("service", "AWS EFS");
                response.put("message", "EFS no está accesible o no se puede escribir");
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
            }

        } catch (Exception e) {
            response.put("status", "DOWN");
            response.put("service", "AWS EFS");
            response.put("message", "Error al verificar EFS: " + e.getMessage());

            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
        }
    }
}