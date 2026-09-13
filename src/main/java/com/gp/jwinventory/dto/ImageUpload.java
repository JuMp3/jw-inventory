package com.gp.jwinventory.dto;

/** Raw uploaded bytes; the service detects and validates the actual image type. */
public record ImageUpload(byte[] data) {
}
