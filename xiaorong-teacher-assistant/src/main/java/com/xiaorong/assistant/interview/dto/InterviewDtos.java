package com.xiaorong.assistant.interview.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import java.time.LocalDateTime;
import java.util.List;

public final class InterviewDtos {
    private InterviewDtos() { }

    public record InterviewerResponse(Long id, String code, String name, String specialty, String style,
                                      String avatar, List<String> supportedPositions) { }

    public record ResumeUpsertRequest(@NotBlank String title, @NotBlank String positionName,
                                      @NotBlank String content) { }

    public record ResumeResponse(Long resumeId, String title, String positionName, String content,
                                 String fileName, String fileType, Long fileSize, String parseStatus,
                                 String parseError, LocalDateTime createTime, LocalDateTime updateTime) { }

    public record ResumeUploadResponse(String title, String positionName, String content, String fileName,
                                       String fileType, Long fileSize, String parseStatus, String parseError) { }

    public record CreateInterviewSessionRequest(@NotBlank String interviewerCode, Long resumeId,
                                                @NotBlank String positionName, String subjectName,
                                                @Min(1) @Max(5) Integer questionCount) { }

    public record InterviewQuestionResponse(Integer questionIndex, String topicName, String keyHint,
                                            List<String> keywords, Integer followUpLevel) { }

    public record SubmitSelfIntroductionRequest(@NotBlank String content) { }

    public record IntroductionFeedbackResponse(String summary, List<String> strengths, List<String> gaps,
                                               Boolean aiGenerated, String providerCode, String model) { }

    public record SelfIntroductionResponse(String content, IntroductionFeedbackResponse feedback) { }

    public record InterviewSessionResponse(Long sessionId, String status, InterviewerResponse interviewer,
                                           ResumeResponse resume, String positionName, String subjectName,
                                           String opening, Integer currentQuestionIndex, Integer questionCount,
                                           InterviewQuestionResponse currentQuestion, Integer totalScore,
                                           LocalDateTime startedAt, LocalDateTime finishedAt,
                                           SelfIntroductionResponse selfIntroduction) { }

    public record SubmitInterviewAnswerRequest(@NotBlank String answerText) { }

    public record InterviewScoreResponse(Integer score, String level, List<String> hitKeywords,
                                         List<String> missKeywords, String interviewerComment, String risk,
                                         Boolean aiGenerated, String providerCode, String model) { }

    public record SubmitInterviewAnswerResponse(String action, InterviewQuestionResponse currentQuestion,
                                                String followUpQuestion, Integer followUpLevel,
                                                InterviewScoreResponse score, Boolean finished) { }

    public record InterviewRecordResponse(Long recordId, Integer questionIndex, String topicName, String keyHint,
                                          String userAnswer, List<String> followUpQuestions,
                                          List<String> followUpAnswers, Integer score, String level,
                                          List<String> hitKeywords, List<String> missKeywords,
                                          String interviewerComment, String risk, LocalDateTime createTime) { }

    public record InterviewReportResponse(Long sessionId, Integer overallScore, String level,
                                          List<String> strengthTags, List<String> weakTags, String summary,
                                          List<String> riskPoints, List<String> suggestions,
                                          List<String> recommendedCourses, Boolean aiGenerated,
                                          String providerCode, String model, LocalDateTime finishedAt) { }

    public record InterviewRecordListItem(Long sessionId, String interviewerName, String positionName,
                                          String status, Integer totalScore, List<String> weakTags,
                                          LocalDateTime startedAt, LocalDateTime finishedAt) { }
}