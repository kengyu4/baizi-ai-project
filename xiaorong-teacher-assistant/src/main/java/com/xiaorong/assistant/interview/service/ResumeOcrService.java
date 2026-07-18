package com.xiaorong.assistant.interview.service;

/**
 * Extracts text from image-only PDF resumes without sending the original file to an AI model.
 */
public interface ResumeOcrService {
    boolean isAvailable();

    OcrResult recognizePdf(byte[] pdfBytes);

    record OcrResult(String text, int pageCount) { }
}
