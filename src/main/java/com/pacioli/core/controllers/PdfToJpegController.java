package com.pacioli.core.controllers;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@RestController
@RequestMapping("/api/pdf")
public class PdfToJpegController {

    @PostMapping(value = "/convert-to-jpeg", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<byte[]> convertPdfToJpeg(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(null);
        }

        try (PDDocument document = PDDocument.load(file.getInputStream())) {
            PDFRenderer pdfRenderer = new PDFRenderer(document);
            ByteArrayOutputStream zipOutputStream = new ByteArrayOutputStream();
            List<String> fileNames = new ArrayList<>();

            try (ZipOutputStream zos = new ZipOutputStream(zipOutputStream)) {
                for (int page = 0; page < document.getNumberOfPages(); page++) {
                    BufferedImage image = pdfRenderer.renderImageWithDPI(page, 300, ImageType.RGB);
                    ByteArrayOutputStream imageStream = new ByteArrayOutputStream();
                    ImageIO.write(image, "png", imageStream);

                    String fileName = "page-" + (page + 1) + ".png";
                    zos.putNextEntry(new ZipEntry(fileName));
                    zos.write(imageStream.toByteArray());
                    zos.closeEntry();

                    fileNames.add(fileName);
                }
            }

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=converted-images.zip")
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(zipOutputStream.toByteArray());
        } catch (IOException e) {
            return ResponseEntity.internalServerError().build();
        }
    }

}
