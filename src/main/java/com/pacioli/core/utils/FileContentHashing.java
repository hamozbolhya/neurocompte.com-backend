package com.pacioli.core.utils;

import org.springframework.util.DigestUtils;

import java.io.IOException;
import java.io.InputStream;

/**
 * MD5 hex digest of uploaded file bytes for duplicate detection (per dossier).
 */
public final class FileContentHashing {

    private FileContentHashing() {
    }

    public static String md5Hex(byte[] content) {
        if (content == null || content.length == 0) {
            return null;
        }
        return DigestUtils.md5DigestAsHex(content);
    }

    public static String md5Hex(InputStream inputStream) throws IOException {
        if (inputStream == null) {
            return null;
        }
        return DigestUtils.md5DigestAsHex(inputStream);
    }
}
