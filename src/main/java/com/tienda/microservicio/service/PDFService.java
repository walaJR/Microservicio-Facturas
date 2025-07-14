package com.tienda.microservicio.service;

import java.io.ByteArrayOutputStream;
import java.time.format.DateTimeFormatter;

import org.springframework.stereotype.Service;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import com.tienda.microservicio.model.Boleta;
import com.tienda.microservicio.model.DetalleBoleta;

@Service
public class PDFService {

    public byte[] generarBoletaPDF(Boleta boleta) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            PdfWriter writer = new PdfWriter(baos);
            PdfDocument pdfDoc = new PdfDocument(writer);
            Document document = new Document(pdfDoc);

            // Título
            Paragraph titulo = new Paragraph("BOLETA DE VENTA")
                    .setFontSize(20)
                    .setBold()
                    .setTextAlignment(TextAlignment.CENTER);
            document.add(titulo);

            // Información de la boleta
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
            Paragraph info = new Paragraph()
                    .add("Boleta N°: " + boleta.getId() + "\n")
                    .add("Fecha: " + boleta.getFecha().format(formatter))
                    .setMarginTop(20)
                    .setMarginBottom(20);
            document.add(info);

            // Tabla de productos
            float[] columnWidths = { 3f, 1f, 2f, 2f };
            Table table = new Table(UnitValue.createPercentArray(columnWidths))
                    .setWidth(UnitValue.createPercentValue(100));

            // Headers
            table.addHeaderCell(new Cell().add(new Paragraph("Producto").setBold()));
            table.addHeaderCell(new Cell().add(new Paragraph("Cantidad").setBold()));
            table.addHeaderCell(new Cell().add(new Paragraph("Precio Unit.").setBold()));
            table.addHeaderCell(new Cell().add(new Paragraph("Subtotal").setBold()));

            // Detalles
            for (DetalleBoleta detalle : boleta.getDetalles()) {
                table.addCell(new Cell().add(new Paragraph(detalle.getProducto().getNombre())));
                table.addCell(new Cell().add(new Paragraph(String.valueOf(detalle.getCantidad()))));
                table.addCell(new Cell().add(new Paragraph(String.format("$%.2f", detalle.getPrecioUnitario()))));
                table.addCell(new Cell().add(new Paragraph(String.format("$%.2f",
                        detalle.getPrecioUnitario() * detalle.getCantidad()))));
            }

            document.add(table);

            // Total
            Paragraph total = new Paragraph()
                    .add("TOTAL: $" + String.format("%.2f", boleta.getTotal()))
                    .setFontSize(16)
                    .setBold()
                    .setTextAlignment(TextAlignment.RIGHT)
                    .setMarginTop(20);
            document.add(total);

            document.close();
            return baos.toByteArray();

        } catch (Exception e) {
            throw new RuntimeException("Error al generar PDF: " + e.getMessage(), e);
        }
    }
}