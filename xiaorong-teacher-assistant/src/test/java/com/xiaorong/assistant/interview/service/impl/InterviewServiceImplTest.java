package com.xiaorong.assistant.interview.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaorong.assistant.auth.AuthContext;
import com.xiaorong.assistant.auth.exception.ForbiddenException;
import com.xiaorong.assistant.auth.model.AuthSession;
import com.xiaorong.assistant.interview.domain.InterviewCatalog;
import com.xiaorong.assistant.interview.domain.InterviewIntroductionFeedback;
import com.xiaorong.assistant.interview.domain.InterviewQuestion;
import com.xiaorong.assistant.interview.domain.InterviewReport;
import com.xiaorong.assistant.interview.domain.InterviewScore;
import com.xiaorong.assistant.interview.dto.InterviewDtos.CreateInterviewSessionRequest;
import com.xiaorong.assistant.interview.dto.InterviewDtos.InterviewSessionResponse;
import com.xiaorong.assistant.interview.dto.InterviewDtos.ResumeUpsertRequest;
import com.xiaorong.assistant.interview.dto.InterviewDtos.SubmitInterviewAnswerRequest;
import com.xiaorong.assistant.interview.dto.InterviewDtos.SubmitSelfIntroductionRequest;
import com.xiaorong.assistant.interview.persistence.MemoryInterviewStore;
import com.xiaorong.assistant.interview.service.InterviewAiService;
import com.xiaorong.assistant.interview.service.ResumeFileParser;
import com.xiaorong.assistant.interview.service.ResumeOcrService;
import com.xiaorong.assistant.study.ai.InterviewFollowUpPolicy;
import com.xiaorong.assistant.study.ai.StudyAiConversationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InterviewServiceImplTest {
    private InterviewServiceImpl service;
    private InterviewAiService aiService;

    @BeforeEach
    void setUp() {
        AuthContext.set(new AuthSession(9L, "student", "Student", List.of("USER")));
        aiService = mock(InterviewAiService.class);
        when(aiService.assessIntroduction(anyString(), anyString())).thenReturn(new InterviewIntroductionFeedback(
                "项目经历清楚，下一步请进一步说明个人技术取舍。", List.of("项目目标明确"), List.of("量化成果"), false, null, null));
        when(aiService.nextQuestion(any(), anyInt())).thenReturn(new InterviewQuestion(
                "你在电商后台项目中负责了什么？", "重点说明个人职责、技术取舍和结果。", List.of("职责", "取舍", "结果"),
                "说明项目背景、个人贡献、技术取舍和可验证结果。", "这项取舍为什么适合当时的场景？", "如果流量增长，你会如何调整？"));
        when(aiService.score(any(), anyString(), anyList())).thenReturn(new InterviewScore(82, "良好",
                List.of("职责"), List.of("量化结果"), "回答较完整。", "建议补充边界情况。", false, null, null));
        when(aiService.review(anyList(), anyString())).thenReturn(new InterviewReport(82, "良好", List.of("职责"),
                List.of("量化结果"), "整体表达清楚。", List.of("补充边界"), List.of("补齐薄弱点"),
                List.of("前端专项题库"), false, null, null));
        service = new InterviewServiceImpl(new MemoryInterviewStore(), new InterviewCatalog(), new ResumeFileParser(disabledOcrService()), aiService,
                new InterviewFollowUpPolicy(), mock(StudyAiConversationService.class), new ObjectMapper());
    }

    private ResumeOcrService disabledOcrService() {
        return new ResumeOcrService() {
            @Override public boolean isAvailable() { return false; }
            @Override public OcrResult recognizePdf(byte[] pdfBytes) { throw new UnsupportedOperationException(); }
        };
    }

    @AfterEach
    void tearDown() { AuthContext.clear(); }

    @Test
    void requiresSelfIntroductionThenKeepsTwoLevelFollowUpsAndCompletedReport() {
        var resume = service.createResume(new ResumeUpsertRequest("前端简历", "前端工程师", "电商后台项目，负责订单模块"));
        InterviewSessionResponse created = service.createSession(new CreateInterviewSessionRequest(
                "lanchuan", resume.resumeId(), "前端工程师", "通用求职", 1));

        assertThat(created.interviewer().name()).isEqualTo("岚川");
        assertThat(created.status()).isEqualTo("self_intro_pending");
        assertThat(created.currentQuestion()).isNull();
        assertThat(created.selfIntroduction()).isNull();
        assertThatThrownBy(() -> service.submitAnswer(created.sessionId(), new SubmitInterviewAnswerRequest("跳过介绍直接回答")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("自我介绍");

        InterviewSessionResponse introduced = service.submitSelfIntroduction(created.sessionId(),
                new SubmitSelfIntroductionRequest("我叫陈宇，应聘前端工程师，最近在电商后台项目中负责订单模块和性能优化。"));
        assertThat(introduced.status()).isEqualTo("interviewing");
        assertThat(introduced.selfIntroduction().content()).contains("电商后台项目");
        assertThat(introduced.selfIntroduction().feedback().summary()).contains("项目经历清楚");
        assertThat(introduced.currentQuestion().topicName()).contains("电商后台项目");

        var first = service.submitAnswer(created.sessionId(), new SubmitInterviewAnswerRequest("我负责订单模块，并根据接口复杂度拆分了状态管理。"));
        assertThat(first.action()).isEqualTo("follow_up");
        assertThat(first.followUpLevel()).isEqualTo(1);
        var second = service.submitAnswer(created.sessionId(), new SubmitInterviewAnswerRequest("因为订单状态很多，需要保证后续维护和联调效率。"));
        assertThat(second.action()).isEqualTo("follow_up");
        assertThat(second.followUpLevel()).isEqualTo(2);
        var finalResponse = service.submitAnswer(created.sessionId(), new SubmitInterviewAnswerRequest("如果流量增长，我会把热点查询做缓存并压测验证。"));

        assertThat(finalResponse.action()).isEqualTo("completed");
        assertThat(finalResponse.finished()).isTrue();
        assertThat(service.getRecords(created.sessionId())).singleElement().satisfies(record ->
                assertThat(record.followUpQuestions()).hasSize(2));
        assertThat(service.getReport(created.sessionId()).overallScore()).isEqualTo(82);
        assertThat(service.listRecords()).singleElement().satisfies(record -> {
            assertThat(record.status()).isEqualTo("finished");
            assertThat(record.totalScore()).isEqualTo(82);
        });
    }

    @Test
    void preventsAnotherUserFromReadingAResumeOrInterviewSession() {
        var resume = service.createResume(new ResumeUpsertRequest("简历", "Java 后端", "Spring Boot"));
        InterviewSessionResponse created = service.createSession(new CreateInterviewSessionRequest(
                "lanchuan", resume.resumeId(), "Java 后端", "通用求职", 1));

        AuthContext.set(new AuthSession(10L, "other", "Other", List.of("USER")));
        assertThat(service.listResumes()).isEmpty();
        assertThatThrownBy(() -> service.getSession(created.sessionId())).isInstanceOf(ForbiddenException.class);
    }
}
