package com.tienda.microservicio.service;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.tienda.microservicio.dto.EfsFileInfo;
import com.tienda.microservicio.dto.RenameResponse;
import com.tienda.microservicio.dto.UploadResponse;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class AwsEfsService {

    @Value("${aws.efs.mount.path:/mnt/efs}")
    private String efsMountPath;

    // Sube un archivo al sistema EFS
    public UploadResponse uploadFile(MultipartFile file) {
        try {
            // Validamos que el directorio EFS existe
            Path efsPath = Paths.get(efsMountPath);
            if (!Files.exists(efsPath)) {
                Files.createDirectories(efsPath);
            }

            // Generamos nombre único para el archivo
            String originalFileName = file.getOriginalFilename();
            String fileName = generateUniqueFileName(originalFileName);

            // Creamos path completo del archivo
            Path filePath = efsPath.resolve(fileName);

            // Guardamos el archivo en EFS
            Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

            log.info("Archivo subido exitosamente a EFS: {}", fileName);

            return new UploadResponse(
                    "Archivo subido exitosamente a EFS",
                    fileName,
                    filePath.toString(),
                    file.getSize(),
                    true);

        } catch (IOException e) {
            log.error("Error al subir archivo a EFS: {}", e.getMessage());
            return new UploadResponse(
                    "Error al subir archivo a EFS: " + e.getMessage(),
                    null,
                    null,
                    0,
                    false);
        }
    }

    // NUEVA FUNCIONALIDAD: Renombrar un archivo en EFS
    public RenameResponse renameFile(String oldFileName, String newFileName) {
        try {
            // Validar que el archivo origen existe
            Path oldPath = Paths.get(efsMountPath, oldFileName);
            if (!Files.exists(oldPath)) {
                log.warn("Archivo origen no encontrado: {}", oldFileName);
                return new RenameResponse(
                        "Archivo origen no encontrado: " + oldFileName,
                        oldFileName,
                        null,
                        null,
                        false);
            }

            // Validar que el nuevo nombre no esté vacío
            if (newFileName == null || newFileName.trim().isEmpty()) {
                return new RenameResponse(
                        "El nuevo nombre de archivo no puede estar vacío",
                        oldFileName,
                        null,
                        null,
                        false);
            }

            // Limpiar el nuevo nombre de archivo (remover caracteres peligrosos)
            String sanitizedNewFileName = sanitizeFileName(newFileName.trim());

            // Verificar que el archivo destino no existe
            Path newPath = Paths.get(efsMountPath, sanitizedNewFileName);
            if (Files.exists(newPath)) {
                log.warn("El archivo destino ya existe: {}", sanitizedNewFileName);
                return new RenameResponse(
                        "Ya existe un archivo con el nombre: " + sanitizedNewFileName,
                        oldFileName,
                        sanitizedNewFileName,
                        null,
                        false);
            }

            // Obtener información del archivo antes del renombrado
            EfsFileInfo oldFileInfo = convertToEfsFileInfo(oldPath);

            // Realizar el renombrado
            Files.move(oldPath, newPath);

            log.info("Archivo renombrado exitosamente de '{}' a '{}'", oldFileName, sanitizedNewFileName);

            return new RenameResponse(
                    "Archivo renombrado exitosamente",
                    oldFileName,
                    sanitizedNewFileName,
                    newPath.toString(),
                    true);

        } catch (IOException e) {
            log.error("Error al renombrar archivo de '{}' a '{}': {}", oldFileName, newFileName, e.getMessage());
            return new RenameResponse(
                    "Error al renombrar archivo: " + e.getMessage(),
                    oldFileName,
                    newFileName,
                    null,
                    false);
        }
    }

    // Listamos todos los archivos del directorio EFS
    public List<EfsFileInfo> listFiles() {
        List<EfsFileInfo> fileList = new ArrayList<>();

        try {
            Path efsPath = Paths.get(efsMountPath);

            if (!Files.exists(efsPath)) {
                log.warn("El directorio EFS no existe: {}", efsMountPath);
                return fileList;
            }

            try (DirectoryStream<Path> stream = Files.newDirectoryStream(efsPath)) {
                for (Path file : stream) {
                    if (Files.isRegularFile(file)) {
                        fileList.add(convertToEfsFileInfo(file));
                    }
                }
            }

        } catch (IOException e) {
            log.error("Error al listar archivos de EFS: {}", e.getMessage());
            throw new RuntimeException("Error al listar archivos de EFS: " + e.getMessage());
        }

        return fileList;
    }

    // Descargar un archivo del EFS
    public byte[] downloadFile(String fileName) {
        try {
            Path filePath = Paths.get(efsMountPath, fileName);

            if (!Files.exists(filePath)) {
                throw new RuntimeException("Archivo no encontrado: " + fileName);
            }

            return Files.readAllBytes(filePath);

        } catch (IOException e) {
            log.error("Error al descargar archivo de EFS: {}", e.getMessage());
            throw new RuntimeException("Error al leer el archivo: " + e.getMessage());
        }
    }

    // Eliminar un archivo del EFS
    public boolean deleteFile(String fileName) {
        try {
            Path filePath = Paths.get(efsMountPath, fileName);

            if (!Files.exists(filePath)) {
                log.warn("Archivo no encontrado para eliminar: {}", fileName);
                return false;
            }

            Files.delete(filePath);
            log.info("Archivo eliminado exitosamente de EFS: {}", fileName);
            return true;

        } catch (IOException e) {
            log.error("Error al eliminar archivo de EFS: {}", e.getMessage());
            return false;
        }
    }

    // Verifica si un archivo existe en EFS
    public boolean fileExists(String fileName) {
        Path filePath = Paths.get(efsMountPath, fileName);
        return Files.exists(filePath) && Files.isRegularFile(filePath);
    }

    // Obtiene información detallada de un archivo
    public EfsFileInfo getFileInfo(String fileName) {
        try {
            Path filePath = Paths.get(efsMountPath, fileName);

            if (!Files.exists(filePath)) {
                throw new RuntimeException("Archivo no encontrado: " + fileName);
            }

            return convertToEfsFileInfo(filePath);

        } catch (IOException e) {
            log.error("Error al obtener información del archivo: {}", e.getMessage());
            throw new RuntimeException("Error al obtener información del archivo: " + e.getMessage());
        }
    }

    // Verifica que el directorio EFS exista y sea accesible
    public boolean isEfsAccessible() {
        try {
            Path efsPath = Paths.get(efsMountPath);

            // Verifica que el directorio existe y es accesible
            if (!Files.exists(efsPath)) {
                return false;
            }

            // Intenta crear un archivo temporal para verificar escritura
            Path tempFile = efsPath.resolve(".health_check_" + System.currentTimeMillis());
            try {
                Files.createFile(tempFile);
                Files.delete(tempFile);
                return true;
            } catch (IOException e) {
                log.warn("No se puede escribir en EFS: {}", e.getMessage());
                return false;
            }

        } catch (Exception e) {
            log.error("Error al verificar accesibilidad de EFS: {}", e.getMessage());
            return false;
        }
    }

    // Métodos auxiliares
    private String generateUniqueFileName(String originalFileName) {
        String extension = "";
        if (originalFileName != null && originalFileName.contains(".")) {
            extension = originalFileName.substring(originalFileName.lastIndexOf("."));
        }
        return UUID.randomUUID().toString() + extension;
    }

    private EfsFileInfo convertToEfsFileInfo(Path filePath) throws IOException {
        String fileName = filePath.getFileName().toString();
        long size = Files.size(filePath);
        Instant lastModified = Files.getLastModifiedTime(filePath).toInstant();
        String contentType = Files.probeContentType(filePath);

        return new EfsFileInfo(
                fileName,
                fileName, // En EFS, el key es el mismo nombre del archivo
                size,
                lastModified,
                contentType,
                filePath.toString() // Path completo como URL
        );
    }

    // NUEVO MÉTODO: Sanitizar nombre de archivo
    private String sanitizeFileName(String fileName) {
        if (fileName == null) {
            return null;
        }

        // Remover caracteres peligrosos para sistemas de archivos
        String sanitized = fileName.replaceAll("[\\\\/:*?\"<>|]", "_");

        // Remover espacios del inicio y final
        sanitized = sanitized.trim();

        // Limitar longitud del nombre (máximo 255 caracteres)
        if (sanitized.length() > 255) {
            sanitized = sanitized.substring(0, 255);
        }

        // Evitar nombres vacíos
        if (sanitized.isEmpty()) {
            sanitized = "renamed_file_" + System.currentTimeMillis();
        }

        return sanitized;
    }
}