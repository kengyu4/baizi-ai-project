package com.xiaorong.assistant.interview.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Extracts textual resumes so all AI providers can be text-only. */
@Component
public class ResumeFileParser {
    private static final long MAX_SIZE = 5L * 1024 * 1024;
    private static final int MAX_TEXT_LENGTH = 120_000;
    private static final int OCR_TEXT_THRESHOLD = 30;

    private final ResumeOcrService ocrService;

    public ResumeFileParser(ResumeOcrService ocrService) {
        this.ocrService = ocrService;
    }

    public ParsedResume parse(MultipartFile file, String positionName) {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("\u8bf7\u5148\u9009\u62e9\u4e00\u4efd\u7b80\u5386\u6587\u4ef6");
        if (file.getSize() > MAX_SIZE) throw new IllegalArgumentException("\u7b80\u5386\u6587\u4ef6\u4e0d\u80fd\u8d85\u8fc7 5 MB");
        String fileName = file.getOriginalFilename() == null ? "resume" : file.getOriginalFilename();
        String lowerName = fileName.toLowerCase(Locale.ROOT);
        try {
            String content;
            String fileType;
            boolean ocrUsed = false;
            int ocrPageCount = 0;
            if (lowerName.endsWith(".pdf")) {
                byte[] pdfBytes = file.getBytes();
                content = extractPdf(pdfBytes);
                fileType = "application/pdf";
                if (content.strip().length() < OCR_TEXT_THRESHOLD) {
                    if (!ocrService.isAvailable()) {
                        throw new IllegalArgumentException("\u626b\u63cf\u4ef6 PDF \u6ca1\u6709\u53ef\u7528\u6587\u5b57\u5c42\u3002\u8bf7\u914d\u7f6e\u767e\u5ea6\u667a\u80fd\u4e91 OCR\uff0c\u6216\u76f4\u63a5\u7c98\u8d34\u7b80\u5386\u6587\u672c\u3002");
                    }
                    ResumeOcrService.OcrResult ocrResult = ocrService.recognizePdf(pdfBytes);
                    content = ocrResult.text();
                    ocrUsed = true;
                    ocrPageCount = ocrResult.pageCount();
                }
            } else if (lowerName.endsWith(".docx")) {
                content = extractDocx(file.getBytes());
                fileType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            } else if (lowerName.endsWith(".txt")) {
                content = new String(file.getBytes(), StandardCharsets.UTF_8);
                fileType = "text/plain";
            } else {
                throw new IllegalArgumentException("\u4ec5\u652f\u6301 PDF\u3001DOCX \u6216 TXT \u683c\u5f0f\u7684\u7b80\u5386");
            }
            content = normalize(content);
            if (content.isBlank()) {
                throw new IllegalArgumentException("\u672a\u80fd\u4ece\u6587\u4ef6\u4e2d\u63d0\u53d6\u6587\u5b57\u3002\u8bf7\u76f4\u63a5\u7c98\u8d34\u7b80\u5386\u6587\u672c\u540e\u4fdd\u5b58\u3002");
            }
            String baseName = fileName.replaceFirst("(?i)\\.(pdf|docx|txt)$", "");
            String title = positionName == null || positionName.isBlank() ? baseName + "\u7b80\u5386" : baseName + " - " + positionName;
            String status = ocrUsed ? "ocr_parsed" : "parsed";
            String parseError = ocrUsed ? "\u5df2\u901a\u8fc7\u767e\u5ea6 OCR \u8bc6\u522b " + ocrPageCount + " \u9875\uff0c\u8bf7\u6838\u5bf9\u6587\u672c\u540e\u4fdd\u5b58\u3002" : null;
            return new ParsedResume(title, content, fileName, fileType, file.getSize(), status, parseError);
        } catch (IOException ex) {
            throw new IllegalArgumentException("\u7b80\u5386\u6587\u4ef6\u89e3\u6790\u5931\u8d25\uff0c\u8bf7\u786e\u8ba4\u6587\u4ef6\u672a\u635f\u574f\u540e\u91cd\u8bd5\u3002");
        }
    }

    private String extractPdf(byte[] bytes) throws IOException {
        try (PDDocument document = PDDocument.load(bytes)) {
            return new PDFTextStripper().getText(document);
        }
    }

    private String extractDocx(byte[] bytes) throws IOException {
        StringBuilder xml = new StringBuilder();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if ("word/document.xml".equals(entry.getName())) {
                    xml.append(new String(zip.readAllBytes(), StandardCharsets.UTF_8));
                    break;
                }
            }
        }
        return xml.toString().replaceAll("</w:p>", "\n").replaceAll("<[^>]+>", " ")
                .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">");
    }

    private String normalize(String value) {
        String text = value.replace('\u0000', ' ').replaceAll("[\\t\\x0B\\f\\r]+", " ")
                .replaceAll(" *\\n *", "\n").replaceAll("\\n{3,}", "\n\n").trim();
        return text.length() > MAX_TEXT_LENGTH ? text.substring(0, MAX_TEXT_LENGTH) : text;
    }

    public record ParsedResume(String title, String content, String fileName, String fileType, Long fileSize,
                               String parseStatus, String parseError) { }
}
