package com.xiaorong.assistant.interview.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaorong.assistant.auth.AuthContext;
import com.xiaorong.assistant.auth.exception.ForbiddenException;
import com.xiaorong.assistant.interview.domain.InterviewCatalog;
import com.xiaorong.assistant.interview.domain.InterviewIntroductionFeedback;
import com.xiaorong.assistant.interview.domain.InterviewQuestion;
import com.xiaorong.assistant.interview.domain.InterviewQuestionContext;
import com.xiaorong.assistant.interview.domain.InterviewReport;
import com.xiaorong.assistant.interview.domain.InterviewScore;
import com.xiaorong.assistant.interview.dto.InterviewDtos.*;
import com.xiaorong.assistant.interview.persistence.InterviewStore;
import com.xiaorong.assistant.interview.persistence.InterviewStore.InterviewerData;
import com.xiaorong.assistant.interview.persistence.InterviewStore.RecordData;
import com.xiaorong.assistant.interview.persistence.InterviewStore.ResumeData;
import com.xiaorong.assistant.interview.persistence.InterviewStore.SessionData;
import com.xiaorong.assistant.interview.service.InterviewAiService;
import com.xiaorong.assistant.interview.service.InterviewService;
import com.xiaorong.assistant.interview.service.ResumeFileParser;
import com.xiaorong.assistant.study.ai.InterviewFollowUpPolicy;
import com.xiaorong.assistant.study.ai.StudyAiConversationService;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class InterviewServiceImpl implements InterviewService {
    private final InterviewStore store;
    private final InterviewCatalog catalog;
    private final ResumeFileParser resumeParser;
    private final InterviewAiService aiService;
    private final InterviewFollowUpPolicy followUpPolicy;
    private final StudyAiConversationService aiConversation;
    private final ObjectMapper objectMapper;

    public InterviewServiceImpl(InterviewStore store, InterviewCatalog catalog, ResumeFileParser resumeParser,
                                InterviewAiService aiService, InterviewFollowUpPolicy followUpPolicy,
                                StudyAiConversationService aiConversation, ObjectMapper objectMapper) {
        this.store = store;
        this.catalog = catalog;
        this.resumeParser = resumeParser;
        this.aiService = aiService;
        this.followUpPolicy = followUpPolicy;
        this.aiConversation = aiConversation;
        this.objectMapper = objectMapper;
    }

    @Override public List<InterviewerResponse> listInterviewers() {
        return store.listInterviewers().stream().map(this::toInterviewer).toList();
    }
    @Override public List<ResumeResponse> listResumes() {
        long userId = AuthContext.requireUserId();
        return store.listResumes(userId).stream().map(this::toResume).toList();
    }
    @Override public ResumeResponse createResume(ResumeUpsertRequest request) {
        long userId = AuthContext.requireUserId();
        return toResume(store.createResume(userId, resumeDraft(request, null)));
    }
    @Override public ResumeResponse updateResume(Long resumeId, ResumeUpsertRequest request) {
        long userId = AuthContext.requireUserId();
        ResumeData previous = store.findResume(userId, resumeId);
        return toResume(store.updateResume(userId, resumeId, resumeDraft(request, previous)));
    }
    @Override public ResumeUploadResponse parseResume(MultipartFile file, String positionName) {
        ResumeFileParser.ParsedResume parsed = resumeParser.parse(file, positionName);
        String position = positionName == null || positionName.isBlank() ? "目标岗位待补充" : positionName;
        return new ResumeUploadResponse(parsed.title(), position, parsed.content(), parsed.fileName(), parsed.fileType(),
                parsed.fileSize(), parsed.parseStatus(), parsed.parseError());
    }

    @Override public InterviewSessionResponse createSession(CreateInterviewSessionRequest request) {
        long userId = AuthContext.requireUserId();
        InterviewerData interviewer = store.listInterviewers().stream()
                .filter(item -> item.code().equals(request.interviewerCode())).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("指定的面试官不存在"));
        ResumeData resume = request.resumeId() == null ? null : store.findResume(userId, request.resumeId());
        int count = request.questionCount() == null ? 3 : request.questionCount();
        SessionData saved = store.createSession(new SessionData(null, userId, interviewer.id(), resume == null ? null : resume.id(),
                interviewer.code(), interviewer.name(), request.positionName(), blankOr(request.subjectName(), request.positionName()),
                "self_intro_pending", count, 0, List.of(), null, null, null,
                0, List.of(), null, null, null, null, null));
        return toSession(saved, resume);
    }

    @Override public InterviewSessionResponse getSession(Long sessionId) {
        SessionData data = owned(sessionId);
        ResumeData resume = data.resumeId() == null ? null : store.findResume(data.userId(), data.resumeId());
        return toSession(data, resume);
    }

    @Override public InterviewSessionResponse submitSelfIntroduction(Long sessionId, SubmitSelfIntroductionRequest request) {
        SessionData session = owned(sessionId);
        if (!"self_intro_pending".equals(session.status())) throw new IllegalArgumentException("当前会话无需再次提交自我介绍");
        ResumeData resume = session.resumeId() == null ? null : store.findResume(session.userId(), session.resumeId());
        String introduction = request.content().trim();
        InterviewIntroductionFeedback feedback = aiService.assessIntroduction(session.positionName(), introduction);
        InterviewQuestionContext context = new InterviewQuestionContext(session.positionName(), resume == null ? "" : resume.content(),
                introduction, List.of());
        InterviewQuestion firstQuestion = aiService.nextQuestion(context, 0);
        try {
            SessionData next = new SessionData(session.id(), session.userId(), session.interviewerId(), session.resumeId(),
                    session.interviewerCode(), session.interviewerName(), session.positionName(), session.subjectName(), "interviewing",
                    questionCount(session), 0, List.of(firstQuestion), introduction, objectMapper.writeValueAsString(feedback),
                    objectMapper.writeValueAsString(context), session.totalScore(), session.weakTags(), session.reportJson(),
                    session.startedAt(), session.finishedAt(), session.createTime(), LocalDateTime.now());
            store.updateSession(next);
            return toSession(next, resume);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("自我介绍反馈保存失败", ex);
        }
    }

    @Override public SubmitInterviewAnswerResponse submitAnswer(Long sessionId, SubmitInterviewAnswerRequest request) {
        SessionData session = owned(sessionId);
        if ("self_intro_pending".equals(session.status())) throw new IllegalArgumentException("请先完成自我介绍，再进入正式面试");
        if (!"interviewing".equals(session.status())) throw new IllegalArgumentException("本场面试已经结束");
        if (session.currentQuestionIndex() >= session.questions().size()) throw new IllegalArgumentException("当前没有可回答的题目");

        int questionOffset = session.currentQuestionIndex();
        InterviewQuestion question = session.questions().get(questionOffset);
        RecordData record = store.listRecords(session.id()).stream()
                .filter(item -> item.questionIndex() == questionOffset + 1).findFirst().orElse(null);
        if (record == null) {
            record = new RecordData(null, session.id(), questionOffset + 1, question.topicName(), question.keyHint(), question.keywords(),
                    question.answerSummary(), request.answerText(), List.of(), List.of(), 0, null, null, List.of(), List.of(),
                    null, null, null, null, null, LocalDateTime.now());
        } else {
            record = withFollowUpAnswer(record, request.answerText());
        }

        int depth = record.followUpLevel();
        InterviewFollowUpPolicy.Decision decision = followUpPolicy.decide(request.answerText(), question.keywords(), depth);
        if (!"none".equals(decision.mode())) {
            String followUp = decideFollowUp(decision, question, request.answerText(), depth);
            if (followUp != null && !followUp.isBlank() && !"NO_FOLLOW_UP".equals(followUp)) {
                int nextLevel = depth + 1;
                record = withFollowUpQuestion(record, followUp, nextLevel);
                store.saveRecord(record);
                return new SubmitInterviewAnswerResponse("follow_up", questionResponse(questionOffset, question, nextLevel), followUp,
                        nextLevel, null, false);
            }
        }

        InterviewScore score = aiService.score(question, record.userAnswer(), record.followUpAnswers());
        store.saveRecord(withScore(record, score));
        int nextQuestionOffset = questionOffset + 1;
        boolean finished = nextQuestionOffset >= questionCount(session);
        SessionData nextSession = session;
        if (finished) {
            nextSession = complete(withQuestionIndex(session, nextQuestionOffset, session.questions(), session.interviewContextJson()));
        } else {
            nextSession = addNextQuestion(session, nextQuestionOffset);
        }
        store.updateSession(nextSession);
        InterviewQuestionResponse nextQuestion = finished ? null
                : questionResponse(nextQuestionOffset, nextSession.questions().get(nextQuestionOffset), 0);
        return new SubmitInterviewAnswerResponse(finished ? "completed" : "scored", nextQuestion, null, 0, toScore(score), finished);
    }

    @Override public InterviewReportResponse getReport(Long sessionId) {
        SessionData session = owned(sessionId);
        if (!"finished".equals(session.status()) || session.reportJson() == null) {
            throw new IllegalArgumentException("面试尚未完成，暂时没有复盘报告");
        }
        try {
            return toReport(session.id(), objectMapper.readValue(session.reportJson(), InterviewReport.class), session.finishedAt());
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("面试复盘数据解析失败", ex);
        }
    }

    @Override public List<InterviewRecordListItem> listRecords() {
        long userId = AuthContext.requireUserId();
        return store.listSessions(userId).stream().map(item -> new InterviewRecordListItem(item.id(), item.interviewerName(),
                item.positionName(), item.status(), item.totalScore(), item.weakTags(), item.startedAt(), item.finishedAt())).toList();
    }
    @Override public List<InterviewRecordResponse> getRecords(Long sessionId) {
        owned(sessionId);
        return store.listRecords(sessionId).stream().map(this::toRecord).toList();
    }

    private SessionData addNextQuestion(SessionData session, int nextQuestionOffset) {
        List<String> askedTopics = session.questions().stream().map(InterviewQuestion::topicName).toList();
        ResumeData resume = session.resumeId() == null ? null : store.findResume(session.userId(), session.resumeId());
        InterviewQuestionContext context = new InterviewQuestionContext(session.positionName(), resume == null ? "" : resume.content(),
                session.selfIntroductionText(), askedTopics);
        InterviewQuestion nextQuestion = aiService.nextQuestion(context, nextQuestionOffset);
        List<InterviewQuestion> questions = new ArrayList<>(session.questions());
        questions.add(nextQuestion);
        try {
            return withQuestionIndex(session, nextQuestionOffset, questions, objectMapper.writeValueAsString(context));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("下一道面试题保存失败", ex);
        }
    }

    private SessionData complete(SessionData session) {
        InterviewReport report = aiService.review(store.listRecords(session.id()), session.positionName());
        try {
            return updateSession(session, "finished", session.currentQuestionIndex(), session.questions(), session.interviewContextJson(),
                    report.overallScore(), report.weakTags(), objectMapper.writeValueAsString(report), LocalDateTime.now());
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("面试复盘数据保存失败", ex);
        }
    }

    private SessionData withQuestionIndex(SessionData session, int questionIndex, List<InterviewQuestion> questions, String contextJson) {
        return updateSession(session, session.status(), questionIndex, questions, contextJson, session.totalScore(), session.weakTags(),
                session.reportJson(), session.finishedAt());
    }

    private SessionData updateSession(SessionData source, String status, int currentQuestionIndex, List<InterviewQuestion> questions,
                                      String contextJson, int totalScore, List<String> weakTags, String reportJson, LocalDateTime finishedAt) {
        return new SessionData(source.id(), source.userId(), source.interviewerId(), source.resumeId(), source.interviewerCode(),
                source.interviewerName(), source.positionName(), source.subjectName(), status, questionCount(source), currentQuestionIndex,
                List.copyOf(questions), source.selfIntroductionText(), source.selfIntroductionFeedbackJson(), contextJson, totalScore,
                weakTags, reportJson, source.startedAt(), finishedAt, source.createTime(), LocalDateTime.now());
    }

    private String decideFollowUp(InterviewFollowUpPolicy.Decision decision, InterviewQuestion question, String answer, int depth) {
        if ("fixed".equals(decision.mode())) return depth == 0 ? question.firstFollowUp() : question.secondFollowUp();
        String result = aiConversation.interviewFollowUp(AuthContext.requireUserId(), question.topicName(), answer, question.keywords(), depth);
        return "NO_FOLLOW_UP".equals(result) ? (depth == 0 ? question.firstFollowUp() : question.secondFollowUp()) : result;
    }
    private SessionData owned(Long sessionId) {
        SessionData data = store.getSession(sessionId);
        if (data.userId() != AuthContext.requireUserId()) throw new ForbiddenException("无权访问该面试会话");
        return data;
    }
    private ResumeData resumeDraft(ResumeUpsertRequest request, ResumeData previous) {
        return new ResumeData(previous == null ? null : previous.id(), previous == null ? null : previous.userId(), request.title(),
                request.positionName(), request.content(), previous == null ? null : previous.fileName(), previous == null ? null : previous.fileType(),
                previous == null ? null : previous.fileSize(), previous == null ? "manual" : previous.parseStatus(),
                previous == null ? null : previous.parseError(), previous == null ? null : previous.createTime(), previous == null ? null : previous.updateTime());
    }
    private RecordData withFollowUpAnswer(RecordData source, String answer) {
        List<String> answers = new ArrayList<>(source.followUpAnswers()); answers.add(answer);
        return new RecordData(source.id(), source.sessionId(), source.questionIndex(), source.topicName(), source.keyHint(), source.keywords(),
                source.answerSummary(), source.userAnswer(), source.followUpQuestions(), answers, source.followUpLevel(), source.score(), source.level(),
                source.hitKeywords(), source.missKeywords(), source.interviewerComment(), source.risk(), source.aiGenerated(), source.providerCode(), source.model(), source.createTime());
    }
    private RecordData withFollowUpQuestion(RecordData source, String question, int level) {
        List<String> questions = new ArrayList<>(source.followUpQuestions()); questions.add(question);
        return new RecordData(source.id(), source.sessionId(), source.questionIndex(), source.topicName(), source.keyHint(), source.keywords(),
                source.answerSummary(), source.userAnswer(), questions, source.followUpAnswers(), level, source.score(), source.level(), source.hitKeywords(),
                source.missKeywords(), source.interviewerComment(), source.risk(), source.aiGenerated(), source.providerCode(), source.model(), source.createTime());
    }
    private RecordData withScore(RecordData source, InterviewScore score) {
        return new RecordData(source.id(), source.sessionId(), source.questionIndex(), source.topicName(), source.keyHint(), source.keywords(),
                source.answerSummary(), source.userAnswer(), source.followUpQuestions(), source.followUpAnswers(), source.followUpLevel(), score.score(),
                score.level(), score.hitKeywords(), score.missKeywords(), score.interviewerComment(), score.risk(), score.aiGenerated(),
                score.providerCode(), score.model(), source.createTime());
    }
    private InterviewSessionResponse toSession(SessionData data, ResumeData resume) {
        InterviewerData interviewer = store.listInterviewers().stream().filter(item -> item.id().equals(data.interviewerId())).findFirst()
                .orElse(new InterviewerData(data.interviewerId(), data.interviewerCode(), data.interviewerName(), "通用求职", "专业追问",
                        "/assets/characters/interviewer-idle.png", List.of("全部岗位")));
        InterviewQuestionResponse current = data.currentQuestionIndex() < data.questions().size()
                ? questionResponse(data.currentQuestionIndex(), data.questions().get(data.currentQuestionIndex()), 0) : null;
        String opening = "self_intro_pending".equals(data.status())
                ? "你好，我是岚川。正式提问前，请用 1—2 分钟介绍你的岗位意向、近期经历，以及项目或实习中的个人职责和结果。"
                : "我是岚川。接下来会结合你的自我介绍、简历和目标岗位逐题提问；每道正式题最多两层追问。";
        return new InterviewSessionResponse(data.id(), data.status(), toInterviewer(interviewer), resume == null ? null : toResume(resume),
                data.positionName(), data.subjectName(), opening, data.currentQuestionIndex(), questionCount(data), current,
                data.totalScore(), data.startedAt(), data.finishedAt(), toSelfIntroduction(data));
    }
    private SelfIntroductionResponse toSelfIntroduction(SessionData data) {
        if (data.selfIntroductionText() == null || data.selfIntroductionText().isBlank()) return null;
        try {
            InterviewIntroductionFeedback feedback = data.selfIntroductionFeedbackJson() == null ? null
                    : objectMapper.readValue(data.selfIntroductionFeedbackJson(), InterviewIntroductionFeedback.class);
            return new SelfIntroductionResponse(data.selfIntroductionText(), feedback == null ? null : toFeedback(feedback));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("自我介绍反馈数据解析失败", ex);
        }
    }
    private IntroductionFeedbackResponse toFeedback(InterviewIntroductionFeedback feedback) {
        return new IntroductionFeedbackResponse(feedback.summary(), feedback.strengths(), feedback.gaps(), feedback.aiGenerated(),
                feedback.providerCode(), feedback.model());
    }
    private InterviewerResponse toInterviewer(InterviewerData source) {
        return new InterviewerResponse(source.id(), source.code(), source.name(), source.specialty(), source.style(), source.avatar(), source.supportedPositions());
    }
    private ResumeResponse toResume(ResumeData source) {
        return new ResumeResponse(source.id(), source.title(), source.positionName(), source.content(), source.fileName(), source.fileType(),
                source.fileSize(), source.parseStatus(), source.parseError(), source.createTime(), source.updateTime());
    }
    private InterviewQuestionResponse questionResponse(int index, InterviewQuestion question, int followUpLevel) {
        return new InterviewQuestionResponse(index + 1, question.topicName(), question.keyHint(), question.keywords(), followUpLevel);
    }
    private InterviewScoreResponse toScore(InterviewScore score) {
        return new InterviewScoreResponse(score.score(), score.level(), score.hitKeywords(), score.missKeywords(), score.interviewerComment(),
                score.risk(), score.aiGenerated(), score.providerCode(), score.model());
    }
    private InterviewRecordResponse toRecord(RecordData source) {
        return new InterviewRecordResponse(source.id(), source.questionIndex(), source.topicName(), source.keyHint(), source.userAnswer(),
                source.followUpQuestions(), source.followUpAnswers(), source.score(), source.level(), source.hitKeywords(), source.missKeywords(),
                source.interviewerComment(), source.risk(), source.createTime());
    }
    private InterviewReportResponse toReport(Long sessionId, InterviewReport report, LocalDateTime finishedAt) {
        return new InterviewReportResponse(sessionId, report.overallScore(), report.level(), report.strengthTags(), report.weakTags(), report.summary(),
                report.riskPoints(), report.suggestions(), report.recommendedCourses(), report.aiGenerated(), report.providerCode(), report.model(), finishedAt);
    }
    private int questionCount(SessionData session) {
        return session.questionCount() > 0 ? session.questionCount() : Math.max(1, session.questions().size());
    }
    private String blankOr(String value, String fallback) { return value == null || value.isBlank() ? fallback : value; }
}