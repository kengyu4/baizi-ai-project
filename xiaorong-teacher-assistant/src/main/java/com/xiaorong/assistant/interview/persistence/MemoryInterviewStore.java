package com.xiaorong.assistant.interview.persistence;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Repository
@ConditionalOnProperty(prefix = "xiaorong.persistence", name = "enabled", havingValue = "false", matchIfMissing = true)
public class MemoryInterviewStore implements InterviewStore {
    private final AtomicLong idGenerator = new AtomicLong(1000);
    private final Map<Long, ResumeData> resumes = new ConcurrentHashMap<>();
    private final Map<Long, SessionData> sessions = new ConcurrentHashMap<>();
    private final Map<Long, List<RecordData>> records = new ConcurrentHashMap<>();
    private final List<InterviewerData> interviewers = List.of(new InterviewerData(
            1L, "lanchuan", "岚川", "通用求职", "冷静专业，逐题追问并给出可执行建议",
            "/assets/characters/interviewer-idle.png", List.of("全部岗位")
    ));

    @Override public List<InterviewerData> listInterviewers() { return interviewers; }
    @Override public ResumeData createResume(long userId, ResumeData data) {
        long id = idGenerator.incrementAndGet(); LocalDateTime now = LocalDateTime.now();
        ResumeData saved = new ResumeData(id, userId, data.title(), data.positionName(), data.content(), data.fileName(),
                data.fileType(), data.fileSize(), data.parseStatus(), data.parseError(), now, now);
        resumes.put(id, saved); return saved;
    }
    @Override public ResumeData updateResume(long userId, long resumeId, ResumeData data) {
        ResumeData old = findResume(userId, resumeId); LocalDateTime now = LocalDateTime.now();
        ResumeData saved = new ResumeData(resumeId, userId, data.title(), data.positionName(), data.content(), data.fileName(),
                data.fileType(), data.fileSize(), data.parseStatus(), data.parseError(), old.createTime(), now);
        resumes.put(resumeId, saved); return saved;
    }
    @Override public List<ResumeData> listResumes(long userId) {
        return resumes.values().stream().filter(item -> item.userId() == userId)
                .sorted(Comparator.comparing(ResumeData::updateTime).reversed()).toList();
    }
    @Override public ResumeData findResume(long userId, long resumeId) {
        ResumeData data = resumes.get(resumeId);
        if (data == null || data.userId() != userId) throw new IllegalArgumentException("简历不存在或无权访问");
        return data;
    }
    @Override public SessionData createSession(SessionData data) {
        long id = idGenerator.incrementAndGet(); LocalDateTime now = LocalDateTime.now();
        SessionData saved = new SessionData(id, data.userId(), data.interviewerId(), data.resumeId(), data.interviewerCode(),
                data.interviewerName(), data.positionName(), data.subjectName(), data.status(), data.questionCount(), data.currentQuestionIndex(),
                data.questions(), data.selfIntroductionText(), data.selfIntroductionFeedbackJson(), data.interviewContextJson(),
                data.totalScore(), data.weakTags(), data.reportJson(), now, null, now, now);
        sessions.put(id, saved); return saved;
    }
    @Override public SessionData getSession(long sessionId) {
        SessionData data = sessions.get(sessionId);
        if (data == null) throw new IllegalArgumentException("面试会话不存在");
        return data;
    }
    @Override public void updateSession(SessionData data) { sessions.put(data.id(), data); }
    @Override public RecordData saveRecord(RecordData data) {
        long id = data.id() == null ? idGenerator.incrementAndGet() : data.id();
        RecordData saved = new RecordData(id, data.sessionId(), data.questionIndex(), data.topicName(), data.keyHint(), data.keywords(),
                data.answerSummary(), data.userAnswer(), data.followUpQuestions(), data.followUpAnswers(), data.followUpLevel(),
                data.score(), data.level(), data.hitKeywords(), data.missKeywords(), data.interviewerComment(), data.risk(),
                data.aiGenerated(), data.providerCode(), data.model(), data.createTime() == null ? LocalDateTime.now() : data.createTime());
        List<RecordData> values = records.computeIfAbsent(data.sessionId(), ignored -> new ArrayList<>());
        synchronized (values) { values.removeIf(item -> item.questionIndex() == data.questionIndex()); values.add(saved); }
        return saved;
    }
    @Override public List<RecordData> listRecords(long sessionId) {
        return records.getOrDefault(sessionId, List.of()).stream().sorted(Comparator.comparing(RecordData::questionIndex)).toList();
    }
    @Override public List<SessionData> listSessions(long userId) {
        return sessions.values().stream().filter(item -> item.userId() == userId)
                .sorted(Comparator.comparing(SessionData::startedAt).reversed()).toList();
    }
}
