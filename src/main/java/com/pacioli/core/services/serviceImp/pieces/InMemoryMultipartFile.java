package com.pacioli.core.services.serviceImp.pieces;

import org.springframework.lang.NonNull;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Objects;

// InMemoryMultipartFile class
public class InMemoryMultipartFile implements MultipartFile {
    private final String name;
    private final String originalFilename;
    private final String contentType;
    private final byte[] content;

    public InMemoryMultipartFile(String name, String originalFilename, String contentType, byte[] content) {
        this.name = Objects.requireNonNull(name, "name");
        this.originalFilename = originalFilename;
        this.contentType = contentType;
        this.content = content != null ? content : new byte[0];
    }

    @Override
    @NonNull
    public String getName() {
        return Objects.requireNonNull(name);
    }

    @Override
    public String getOriginalFilename() {
        return originalFilename;
    }

    @Override
    public String getContentType() {
        return contentType;
    }

    @Override
    public boolean isEmpty() {
        return content.length == 0;
    }

    @Override
    public long getSize() {
        return content.length;
    }

    @Override
    @NonNull
    public byte[] getBytes() throws IOException {
        return Objects.requireNonNull(content);
    }

    @Override
    @NonNull
    public InputStream getInputStream() throws IOException {
        return new java.io.ByteArrayInputStream(content);
    }

    @Override
    public void transferTo(@NonNull java.io.File dest) throws IOException, IllegalStateException {
        Files.write(dest.toPath(), content);
    }
}