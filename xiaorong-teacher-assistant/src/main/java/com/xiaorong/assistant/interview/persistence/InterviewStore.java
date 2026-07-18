package com.xiaorong.assistant.interview.persistence;

import com.xiaorong.assistant.interview.domain.InterviewQuestion;

import java.time.LocalDateTime;
import java.util.List;

public interface InterviewStore {
    List<InterviewerData> listInterviewers();
    ResumeData createResume(long userId, ResumeData data);
    ResumeData updateResume(long userId, long resumeId, ResumeData data);
    List<ResumeData> listResumes(long userId);
    ResumeData findResume(long userId, long resumeId);
    SessionData createSession(SessionData data);
    SessionData getSession(long sessionId);
    void updateSession(SessionData data);
    RecordData saveRecord(RecordData data);
    List<RecordData> listRecords(long sessionId);
    List<SessionData> listSessions(long userId);

    record InterviewerData(Long id, String code, String name, String specialty, String style,
                           String avatar, List<String> supportedPositions) { }
    record ResumeData(Long id, Long userId, String title, String positionName, String content,
                      String fileName, String fileType, Long fileSize, String parseStatus, String parseError,
                      LocalDateTime createTime, LocalDateTime updateTime) { }
    record SessionData(Long id, Long userId, Long interviewerId, Long resumeId, String interviewerCode,
                       String interviewerName, String positionName, String subjectName, String status,
                       int questionCount, int currentQuestionIndex, List<InterviewQuestion> questions,
                       String selfIntroductionText, String selfIntroductionFeedbackJson, String interviewContextJson,
                       int totalScore, List<String> weakTags, String reportJson, LocalDateTime startedAt,
                       LocalDateTime finishedAt, LocalDateTime createTime, LocalDateTime updateTime) { }
    record RecordData(Long id, Long sessionId, int questionIndex, String topicName, String keyHint,
                      List<String> keywords, String answerSummary, String userAnswer,
                      List<String> followUpQuestions, List<String> followUpAnswers, int followUpLevel,
                      Integer score, String level, List<String> hitKeywords, List<String> missKeywords,
                      String interviewerComment, String risk, Boolean aiGenerated, String providerCode,
                      String model, LocalDateTime createTime) { }
}