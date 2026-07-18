package com.xiaorong.assistant.interview.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

class ResumeFileParserTest {
    @Test
    void usesOcrForPdfWithoutUsableTextLayer() throws Exception {
        ResumeOcrService ocr = new ResumeOcrService() {
            @Override public boolean isAvailable() { return true; }
            @Override public OcrResult recognizePdf(byte[] pdfBytes) {
                return new OcrResult("??\nJava ?????", 1);
            }
        };
        ResumeFileParser parser = new ResumeFileParser(ocr);

        ResumeFileParser.ParsedResume parsed = parser.parse(
                new MockMultipartFile("file", "resume.pdf", "application/pdf", blankPdf()), "Java ?????");

        assertThat(parsed.content()).contains("Java ?????");
        assertThat(parsed.parseStatus()).isEqualTo("ocr_parsed");
        assertThat(parsed.parseError()).isNotBlank().contains("1");
    }

    @Test
    void preservesOcrLineBreaksInsteadOfReplacingThemWithLetterN() throws Exception {
        ResumeOcrService ocr = new ResumeOcrService() {
            @Override public boolean isAvailable() { return true; }
            @Override public OcrResult recognizePdf(byte[] pdfBytes) {
                return new OcrResult("Name\nSkills\nExperience", 1);
            }
        };

        ResumeFileParser parser = new ResumeFileParser(ocr);
        ResumeFileParser.ParsedResume parsed = parser.parse(
                new MockMultipartFile("file", "resume.pdf", "application/pdf", blankPdf()), "General");

        assertThat(parsed.content()).isEqualTo("Name\nSkills\nExperience");
    }
    @Test
    void preservesDocxParagraphBreaksAsLineBreaks() {
        ResumeFileParser parser = new ResumeFileParser(new ResumeOcrService() {
            @Override public boolean isAvailable() { return false; }
            @Override public OcrResult recognizePdf(byte[] pdfBytes) { throw new UnsupportedOperationException(); }
        });

        ResumeFileParser.ParsedResume parsed = parser.parse(
                new MockMultipartFile("file", "resume.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", docx()), "General");

        assertThat(parsed.content()).isEqualTo("Name\nSkills");
    }

    private byte[] docx() {
        String document = "<w:document><w:body><w:p><w:r><w:t>Name</w:t></w:r></w:p><w:p><w:r><w:t>Skills</w:t></w:r></w:p></w:body></w:document>";
        try (ByteArrayOutputStream output = new ByteArrayOutputStream(); ZipOutputStream zip = new ZipOutputStream(output)) {
            zip.putNextEntry(new ZipEntry("word/document.xml"));
            zip.write(document.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.finish();
            return output.toByteArray();
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }
    private byte[] blankPdf() throws Exception {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.addPage(new PDPage());
            document.save(output);
            return output.toByteArray();
        }
    }
}
