package com.pacioli.core.services.serviceImp.pieces;

import com.pacioli.core.services.ConfigurationService;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;

@Slf4j
@Service
public class FileService {

    @Autowired
    private ConfigurationService configurationService;

    @Value("${file.upload.dir:Files/}")
    private String uploadDir;


    public FileProcessingResult validateAndSaveFile(MultipartFile file, String pieceType) throws IOException {
        // Check if file validation is enabled
        if (!isFileValidationEnabled()) {
            log.info("File validation is disabled, skipping validation");
            return saveFileWithoutValidation(file, pieceType);
        }

        if (file == null || file.isEmpty()) {
            throw new IOException("Aucun fichier fourni.");
        }

        // Validate file size
        validateFileSize(file);

        String originalFilename = file.getOriginalFilename();
        String fileExtension = getFileExtension(originalFilename);

        // Validate file type against allowed extensions from database
        if (!isValidFileType(fileExtension)) {
            Set<String> allowedExtensions = getAllowedExtensions();
            throw new IOException(String.format(
                    "Type de fichier non accepté. Extensions autorisées: %s",
                    String.join(", ", allowedExtensions)
            ));
        }

        log.info("✅ File type validation OK: {}", fileExtension);

        // Check if PDF should be converted to image
        boolean isPdf = "pdf".equalsIgnoreCase(fileExtension);
        boolean isBankStatement = "Relevés bancaires".equalsIgnoreCase(pieceType);
        boolean convertPdfToImage = shouldConvertPdfToImage();

        log.info("PDF conversion setting: {}, Is PDF: {}, Is Bank Statement: {}",
                convertPdfToImage, isPdf, isBankStatement);

        // Only convert if: conversion is enabled, file is PDF, and NOT a bank statement
        if (convertPdfToImage && isPdf && !isBankStatement) {
            return convertAndSavePdf(file);
        } else {
            String uuid = UUID.randomUUID().toString();
            String formattedFilename = uuid + "." + fileExtension;
            saveFileToDisk(file, formattedFilename);
            return new FileProcessingResult(formattedFilename, file);
        }
    }

    private FileProcessingResult saveFileWithoutValidation(MultipartFile file, String pieceType) throws IOException {
        String originalFilename = file.getOriginalFilename();
        String fileExtension = getFileExtension(originalFilename);

        boolean isPdf = "pdf".equalsIgnoreCase(fileExtension);
        boolean isBankStatement = "Relevés bancaires".equalsIgnoreCase(pieceType);
        boolean convertPdfToImage = shouldConvertPdfToImage();

        if (convertPdfToImage && isPdf && !isBankStatement) {
            return convertAndSavePdf(file);
        } else {
            String uuid = UUID.randomUUID().toString();
            String formattedFilename = uuid + "." + fileExtension;
            saveFileToDisk(file, formattedFilename);
            return new FileProcessingResult(formattedFilename, file);
        }
    }

    private void validateFileSize(MultipartFile file) throws IOException {
        long maxFileSizeBytes = getMaxFileSizeBytes();

        if (file.getSize() > maxFileSizeBytes) {
            double maxSizeMB = maxFileSizeBytes / (1024.0 * 1024.0);
            double fileSizeMB = file.getSize() / (1024.0 * 1024.0);
            throw new IOException(String.format(
                    "Fichier trop volumineux. Taille maximale: %.2f MB, Taille du fichier: %.2f MB",
                    maxSizeMB, fileSizeMB
            ));
        }
    }

    // Configuration getters
    private boolean isFileValidationEnabled() {
        return configurationService.getBooleanConfig("enable_file_validation", true);
    }

    private boolean shouldConvertPdfToImage() {
        return configurationService.getBooleanConfig("convert_pdf_to_image", false);
    }

    private int getPdfConversionDpi() {
        return configurationService.getIntConfig("pdf_conversion_dpi", 300);
    }

    private int getPdfConversionMaxPages() {
        return configurationService.getIntConfig("pdf_conversion_max_pages", 1);
    }

    private long getMaxFileSizeBytes() {
        // Try to get from max_file_size_mb first, then fallback to max_image_size_bytes
        int maxFileSizeMB = configurationService.getIntConfig("max_file_size_mb", 10);
        long maxSizeFromMB = maxFileSizeMB * 1024L * 1024L;

        // Also check max_image_size_bytes for backward compatibility
        long maxImageSizeBytes = configurationService.getIntConfig("max_image_size_bytes", 2 * 1024 * 1024);

        // Return the larger of the two (or maxFileSizeMB if both are set)
        return Math.max(maxSizeFromMB, maxImageSizeBytes);
    }

    private Set<String> getAllowedExtensions() {
        String extensionsConfig = configurationService.getConfigValue(
                "allowed_file_extensions",
                "pdf,jpg,jpeg,png"
        );

        Set<String> extensions = new HashSet<>();
        if (extensionsConfig != null && !extensionsConfig.trim().isEmpty()) {
            String[] parts = extensionsConfig.split(",");
            for (String part : parts) {
                String ext = part.trim().toLowerCase();
                if (!ext.isEmpty()) {
                    extensions.add(ext);
                }
            }
        }

        // Ensure at least default extensions
        if (extensions.isEmpty()) {
            extensions.addAll(Set.of("pdf", "jpg", "jpeg", "png"));
        }

        return extensions;
    }

    private boolean isValidFileType(String fileExtension) {
        if (fileExtension == null) return false;
        return getAllowedExtensions().contains(fileExtension.toLowerCase());
    }

    private FileProcessingResult convertAndSavePdf(MultipartFile pdfFile) throws IOException {
        try {
            log.info("Converting PDF to PNG (first page only)");

            int maxPages = getPdfConversionMaxPages();
            // Convert PDF to PNG images
            List<MultipartFile> convertedImages = convertPdfToPngImages(pdfFile, maxPages);

            if (convertedImages.isEmpty()) {
                log.warn("No images were converted from the PDF, falling back to save as-is");
                return saveAsOriginal(pdfFile);
            }

            // Use first page as the converted image
            MultipartFile firstPageImage = convertedImages.get(0);
            String uuid = UUID.randomUUID().toString();
            String formattedFilename = uuid + ".png"; // Save as PNG

            saveFileToDisk(firstPageImage, formattedFilename);
            log.info("✅ PDF converted and saved as PNG: {}", formattedFilename);

            // Return the converted PNG file, not the original PDF
            return new FileProcessingResult(formattedFilename, firstPageImage);

        } catch (Exception e) {
            log.error("Error during PDF conversion: {}", e.getMessage(), e);
            log.info("Falling back to saving the PDF as-is");
            return saveAsOriginal(pdfFile);
        }
    }

    private List<MultipartFile> convertPdfToPngImages(MultipartFile pdfFile, int maxPages) throws IOException {
        List<MultipartFile> imageFiles = new ArrayList<>();

        // Use try-with-resources for PDDocument
        try (PDDocument document = PDDocument.load(pdfFile.getInputStream())) {
            log.info("PDF loaded successfully. Number of pages: {}", document.getNumberOfPages());

            PDFRenderer pdfRenderer = new PDFRenderer(document);
            pdfRenderer.setSubsamplingAllowed(false);

            int totalPages = document.getNumberOfPages();
            int pagesToProcess = maxPages > 0 ? Math.min(maxPages, totalPages) : 1; // Limit to 1 page for large images

            log.info("Converting {} of {} pages", pagesToProcess, totalPages);

            // **REDUCE DPI for very large PDFs to avoid memory issues**
            int dpi = getPdfConversionDpi();

            // Check if this is a very large PDF (like A4 at 300+ DPI)
            if (document.getNumberOfPages() > 0) {
                // Reduce DPI for large documents to prevent OOM
                dpi = Math.min(dpi, 150); // Max 150 DPI to prevent huge images
                log.info("Large PDF detected, reducing DPI to: {}", dpi);
            }

            for (int page = 0; page < pagesToProcess; page++) {
                log.info("Processing page {}", (page + 1));

                // Render with reduced DPI
                BufferedImage image = pdfRenderer.renderImageWithDPI(page, dpi, ImageType.RGB);
                log.info("Page {} rendered. Image dimensions: {}x{}",
                        (page + 1), image.getWidth(), image.getHeight());

                // **SCALE DOWN VERY LARGE IMAGES IMMEDIATELY**
                if (image.getWidth() > 2000 || image.getHeight() > 2000) {
                    log.info("Image is very large, scaling down...");
                    double scaleFactor = 2000.0 / Math.max(image.getWidth(), image.getHeight());
                    int newWidth = (int) (image.getWidth() * scaleFactor);
                    int newHeight = (int) (image.getHeight() * scaleFactor);

                    image = resizeImage(image, newWidth, newHeight);
                    log.info("Scaled to: {}x{}", newWidth, newHeight);
                }

                byte[] imageBytes;
                try {
                    // Use lower quality for large images to reduce size
                    float quality = 0.7f; // Reduced quality for smaller file size
                    imageBytes = compressImageToPngBytes(image, quality);
                } catch (Exception e) {
                    log.error("Error compressing page {}, trying fallback compression: {}",
                            (page + 1), e.getMessage());

                    // Fallback: Use JPEG instead of PNG
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    ImageIO.write(image, "jpg", baos);
                    imageBytes = baos.toByteArray();
                }

                String imageName = "page-" + (page + 1) + ".png";

                MultipartFile imageFile = new InMemoryMultipartFile(
                        imageName,
                        imageName,
                        "image/png",
                        imageBytes
                );

                imageFiles.add(imageFile);

                // **CLEAR MEMORY** - help GC by nulling reference
                image.flush();

                log.info("Added PNG image to list: {} (size: {} bytes)", imageName, imageBytes.length);

                // Break if we have too many images to prevent OOM
                if (imageFiles.size() >= 5) {
                    log.warn("Stopping conversion after {} pages to prevent memory issues", imageFiles.size());
                    break;
                }
            }

            log.info("PDF to PNG conversion completed. Total images: {}", imageFiles.size());

        } catch (Exception e) {
            log.error("Error converting PDF to PNG images: {}", e.getMessage(), e);
            throw new IOException("Failed to convert PDF to PNG images", e);
        }

        return imageFiles;
    }

    private FileProcessingResult saveAsOriginal(MultipartFile file) throws IOException {
        String originalFilename = file.getOriginalFilename();
        String fileExtension = getFileExtension(originalFilename);
        String uuid = UUID.randomUUID().toString();
        String formattedFilename = uuid + "." + fileExtension;
        saveFileToDisk(file, formattedFilename);
        return new FileProcessingResult(formattedFilename, file);
    }

    private byte[] compressImageToPngBytes(BufferedImage image, float quality) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageWriter writer = null;
        ImageOutputStream ios = null;

        try {
            Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("png");
            if (!writers.hasNext()) {
                // Fallback to basic ImageIO.write
                ImageIO.write(image, "png", baos);
                return baos.toByteArray();
            }

            writer = writers.next();
            ImageWriteParam param = writer.getDefaultWriteParam();

            if (param.canWriteCompressed()) {
                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                param.setCompressionType("Deflate");
                param.setCompressionQuality(quality);
            }

            ios = ImageIO.createImageOutputStream(baos);
            writer.setOutput(ios);
            writer.write(null, new IIOImage(image, null, null), param);

            // IMPORTANT: Flush and close in correct order
            ios.flush();

        } catch (Exception e) {
            log.error("Error compressing PNG image: {}", e.getMessage(), e);
            throw new IOException("Failed to compress PNG image", e);
        } finally {
            // Clean up resources in correct order
            try {
                if (writer != null) {
                    writer.dispose();
                }
            } catch (Exception e) {
                log.warn("Error disposing ImageWriter: {}", e.getMessage());
            }

            try {
                if (ios != null) {
                    ios.close();
                }
            } catch (Exception e) {
                log.warn("Error closing ImageOutputStream: {}", e.getMessage());
            }

            try {
                baos.close();
            } catch (Exception e) {
                log.warn("Error closing ByteArrayOutputStream: {}", e.getMessage());
            }
        }

        return baos.toByteArray();
    }

    private BufferedImage resizeImage(BufferedImage originalImage, int targetWidth, int targetHeight) {
        BufferedImage resizedImage = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g2d = resizedImage.createGraphics();
        g2d.drawImage(originalImage, 0, 0, targetWidth, targetHeight, null);
        g2d.dispose();
        return resizedImage;
    }

    private void saveFileToDisk(MultipartFile file, String formattedFilename) throws IOException {
        Path uploadPath = Paths.get(uploadDir);
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }

        Path filePath = uploadPath.resolve(formattedFilename);
        Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);
    }

    private String getFileExtension(String filename) {
        if (filename == null || !filename.contains(".")) return null;
        return filename.substring(filename.lastIndexOf(".") + 1).toLowerCase();
    }

    // Helper method to check if file is PDF
    public boolean isPdfFile(MultipartFile file) throws IOException {
        String filename = file.getOriginalFilename();
        String contentType = file.getContentType();

        log.info("Checking if file is PDF. Filename: {}, Content Type: {}", filename, contentType);

        boolean hasExtension = (filename != null && filename.toLowerCase().endsWith(".pdf"));
        boolean hasContentType = (contentType != null &&
                (contentType.equals("application/pdf") || contentType.equals("application/x-pdf")));

        log.info("Has PDF extension: {}, Has PDF content type: {}", hasExtension, hasContentType);

        // Only check header if both filename and content-type checks fail
        if (!hasExtension && !hasContentType) {
            try (InputStream inputStream = file.getInputStream()) {
                if (!inputStream.markSupported()) {
                    try (BufferedInputStream bufferedStream = new BufferedInputStream(inputStream)) {
                        return checkPdfHeader(bufferedStream);
                    }
                }
                return checkPdfHeader(inputStream);
            }
        }

        return hasExtension || hasContentType;
    }

    private boolean checkPdfHeader(InputStream inputStream) throws IOException {
        inputStream.mark(5);
        byte[] header = new byte[5];
        int bytesRead = inputStream.read(header);
        inputStream.reset();

        if (bytesRead >= 5) {
            String headerStr = new String(header);
            boolean hasPdfHeader = headerStr.startsWith("%PDF-");
            log.info("Has PDF header: {}", hasPdfHeader);
            return hasPdfHeader;
        }
        return false;
    }
}