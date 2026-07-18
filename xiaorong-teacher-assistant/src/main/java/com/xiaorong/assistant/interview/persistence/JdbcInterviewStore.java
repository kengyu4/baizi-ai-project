package com.xiaorong.assistant.interview.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaorong.assistant.interview.domain.InterviewQuestion;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Repository
@ConditionalOnProperty(prefix = "xiaorong.persistence", name = "enabled", havingValue = "true")
public class JdbcInterviewStore implements InterviewStore {
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public JdbcInterviewStore(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Override public List<InterviewerData> listInterviewers() {
        return jdbc.query("select id, code, name, specialty, style, avatar, supported_positions from ai_interviewer where enabled=1 order by id",
                (rs, rowNum) -> new InterviewerData(rs.getLong("id"), rs.getString("code"), rs.getString("name"),
                        rs.getString("specialty"), rs.getString("style"), rs.getString("avatar"), strings(rs.getString("supported_positions"))));
    }

    @Override public ResumeData createResume(long userId, ResumeData data) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    insert into ai_interview_resume
                    (user_id,title,position_name,content_text,file_name,file_type,file_size,parse_status,parse_error,create_time,update_time)
                    values (?,?,?,?,?,?,?,?,?,now(),now())
                    """, Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, userId); statement.setString(2, data.title()); statement.setString(3, data.positionName());
            statement.setString(4, data.content()); statement.setString(5, data.fileName()); statement.setString(6, data.fileType());
            if (data.fileSize() == null) statement.setNull(7, java.sql.Types.BIGINT); else statement.setLong(7, data.fileSize());
            statement.setString(8, data.parseStatus()); statement.setString(9, data.parseError());
            return statement;
        }, keyHolder);
        return findResume(userId, Objects.requireNonNull(keyHolder.getKey(), "新增简历未返回主键").longValue());
    }

    @Override public ResumeData updateResume(long userId, long resumeId, ResumeData data) {
        findResume(userId, resumeId);
        jdbc.update("""
                update ai_interview_resume set title=?,position_name=?,content_text=?,file_name=?,file_type=?,file_size=?,
                parse_status=?,parse_error=?,update_time=now() where id=? and user_id=?
                """, data.title(), data.positionName(), data.content(), data.fileName(), data.fileType(), data.fileSize(),
                data.parseStatus(), data.parseError(), resumeId, userId);
        return findResume(userId, resumeId);
    }

    @Override public List<ResumeData> listResumes(long userId) {
        return jdbc.query("select * from ai_interview_resume where user_id=? order by update_time desc", (rs, rowNum) -> resume(rs), userId);
    }
    @Override public ResumeData findResume(long userId, long resumeId) {
        try {
            return jdbc.queryForObject("select * from ai_interview_resume where id=? and user_id=?", (rs, rowNum) -> resume(rs), resumeId, userId);
        } catch (EmptyResultDataAccessException ex) {
            throw new IllegalArgumentException("简历不存在或无权访问");
        }
    }

    @Override public SessionData createSession(SessionData data) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    insert into ai_interview_session
                    (user_id,interviewer_id,resume_id,interviewer_code,interviewer_name,position_name,subject_name,status,
                    question_count,current_question_index,question_json,self_intro_text,self_intro_feedback_json,interview_context_json,
                    total_score,weak_tags,report_json,started_at,create_time,update_time)
                    values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,now(),now(),now())
                    """, Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, data.userId()); statement.setLong(2, data.interviewerId());
            if (data.resumeId() == null) statement.setNull(3, java.sql.Types.BIGINT); else statement.setLong(3, data.resumeId());
            statement.setString(4, data.interviewerCode()); statement.setString(5, data.interviewerName()); statement.setString(6, data.positionName());
            statement.setString(7, data.subjectName()); statement.setString(8, data.status()); statement.setInt(9, data.questionCount());
            statement.setInt(10, data.currentQuestionIndex()); statement.setString(11, json(data.questions()));
            statement.setString(12, data.selfIntroductionText()); statement.setString(13, data.selfIntroductionFeedbackJson());
            statement.setString(14, data.interviewContextJson()); statement.setInt(15, data.totalScore());
            statement.setString(16, json(data.weakTags())); statement.setString(17, data.reportJson()); return statement;
        }, keyHolder);
        return getSession(Objects.requireNonNull(keyHolder.getKey(), "新增面试会话未返回主键").longValue());
    }

    @Override public SessionData getSession(long sessionId) {
        try {
            return jdbc.queryForObject("select * from ai_interview_session where id=?", (rs, rowNum) -> session(rs), sessionId);
        } catch (EmptyResultDataAccessException ex) {
            throw new IllegalArgumentException("面试会话不存在");
        }
    }
    @Override public void updateSession(SessionData data) {
        jdbc.update("""
                update ai_interview_session set status=?,question_count=?,current_question_index=?,question_json=?,self_intro_text=?,
                self_intro_feedback_json=?,interview_context_json=?,total_score=?,weak_tags=?,report_json=?,finished_at=?,update_time=now() where id=?
                """, data.status(), data.questionCount(), data.currentQuestionIndex(), json(data.questions()), data.selfIntroductionText(),
                data.selfIntroductionFeedbackJson(), data.interviewContextJson(), data.totalScore(), json(data.weakTags()), data.reportJson(),
                data.finishedAt() == null ? null : Timestamp.valueOf(data.finishedAt()), data.id());
    }

    @Override public RecordData saveRecord(RecordData data) {
        if (data.id() == null) return insertRecord(data);
        jdbc.update("""
                update ai_interview_record set user_answer=?,follow_up_questions=?,follow_up_answers=?,follow_up_level=?,score=?,level=?,
                hit_keywords=?,miss_keywords=?,interviewer_comment=?,risk=?,ai_generated=?,provider_code=?,model=? where id=?
                """, data.userAnswer(), json(data.followUpQuestions()), json(data.followUpAnswers()), data.followUpLevel(), data.score(),
                data.level(), json(data.hitKeywords()), json(data.missKeywords()), data.interviewerComment(), data.risk(),
                data.aiGenerated(), data.providerCode(), data.model(), data.id());
        return findRecord(data.id());
    }
    private RecordData insertRecord(RecordData data) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    insert into ai_interview_record
                    (session_id,question_index,topic_name,key_hint,keywords,answer_summary,user_answer,follow_up_questions,
                    follow_up_answers,follow_up_level,score,level,hit_keywords,miss_keywords,interviewer_comment,risk,
                    ai_generated,provider_code,model,create_time)
                    values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,now())
                    """, Statement.RETURN_GENERATED_KEYS);
            bindRecord(statement, data); return statement;
        }, keyHolder);
        return findRecord(Objects.requireNonNull(keyHolder.getKey(), "新增面试答题记录未返回主键").longValue());
    }
    @Override public List<RecordData> listRecords(long sessionId) {
        return jdbc.query("select * from ai_interview_record where session_id=? order by question_index", (rs, rowNum) -> record(rs), sessionId);
    }
    @Override public List<SessionData> listSessions(long userId) {
        return jdbc.query("select * from ai_interview_session where user_id=? order by started_at desc", (rs, rowNum) -> session(rs), userId);
    }

    private void bindRecord(PreparedStatement statement, RecordData data) throws java.sql.SQLException {
        statement.setLong(1, data.sessionId()); statement.setInt(2, data.questionIndex()); statement.setString(3, data.topicName());
        statement.setString(4, data.keyHint()); statement.setString(5, json(data.keywords())); statement.setString(6, data.answerSummary());
        statement.setString(7, data.userAnswer()); statement.setString(8, json(data.followUpQuestions())); statement.setString(9, json(data.followUpAnswers()));
        statement.setInt(10, data.followUpLevel());
        if (data.score() == null) statement.setNull(11, java.sql.Types.INTEGER); else statement.setInt(11, data.score());
        statement.setString(12, data.level()); statement.setString(13, json(data.hitKeywords())); statement.setString(14, json(data.missKeywords()));
        statement.setString(15, data.interviewerComment()); statement.setString(16, data.risk()); statement.setObject(17, data.aiGenerated());
        statement.setString(18, data.providerCode()); statement.setString(19, data.model());
    }

    private ResumeData resume(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new ResumeData(rs.getLong("id"), rs.getLong("user_id"), rs.getString("title"), rs.getString("position_name"),
                rs.getString("content_text"), rs.getString("file_name"), rs.getString("file_type"), nullableLong(rs, "file_size"),
                rs.getString("parse_status"), rs.getString("parse_error"), time(rs, "create_time"), time(rs, "update_time"));
    }
    private SessionData session(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new SessionData(rs.getLong("id"), rs.getLong("user_id"), rs.getLong("interviewer_id"), nullableLong(rs, "resume_id"),
                rs.getString("interviewer_code"), rs.getString("interviewer_name"), rs.getString("position_name"), rs.getString("subject_name"),
                rs.getString("status"), rs.getInt("question_count"), rs.getInt("current_question_index"), questions(rs.getString("question_json")),
                rs.getString("self_intro_text"), rs.getString("self_intro_feedback_json"), rs.getString("interview_context_json"),
                rs.getInt("total_score"), strings(rs.getString("weak_tags")), rs.getString("report_json"), time(rs, "started_at"),
                time(rs, "finished_at"), time(rs, "create_time"), time(rs, "update_time"));
    }
    private RecordData record(java.sql.ResultSet rs) throws java.sql.SQLException {
        Object generated = rs.getObject("ai_generated");
        Boolean aiGenerated = generated == null ? null : rs.getBoolean("ai_generated");
        return new RecordData(rs.getLong("id"), rs.getLong("session_id"), rs.getInt("question_index"), rs.getString("topic_name"),
                rs.getString("key_hint"), strings(rs.getString("keywords")), rs.getString("answer_summary"), rs.getString("user_answer"),
                strings(rs.getString("follow_up_questions")), strings(rs.getString("follow_up_answers")), rs.getInt("follow_up_level"),
                (Integer) rs.getObject("score"), rs.getString("level"), strings(rs.getString("hit_keywords")), strings(rs.getString("miss_keywords")),
                rs.getString("interviewer_comment"), rs.getString("risk"), aiGenerated, rs.getString("provider_code"), rs.getString("model"),
                time(rs, "create_time"));
    }
    private RecordData findRecord(long id) {
        return jdbc.queryForObject("select * from ai_interview_record where id=?", (rs, rowNum) -> record(rs), id);
    }
    private LocalDateTime time(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        Timestamp value = rs.getTimestamp(column); return value == null ? null : value.toLocalDateTime();
    }
    private Long nullableLong(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        long value = rs.getLong(column); return rs.wasNull() ? null : value;
    }
    private String json(Object value) {
        try { return objectMapper.writeValueAsString(value == null ? List.of() : value); }
        catch (JsonProcessingException ex) { throw new IllegalStateException("面试数据序列化失败", ex); }
    }
    private List<String> strings(String value) {
        try { return value == null || value.isBlank() ? List.of() : objectMapper.readValue(value, new TypeReference<>() { }); }
        catch (JsonProcessingException ex) { return List.of(); }
    }
    private List<InterviewQuestion> questions(String value) {
        try {
            return value == null || value.isBlank() ? List.of() : objectMapper.readValue(value,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, InterviewQuestion.class));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("面试题目数据解析失败", ex);
        }
    }
}
