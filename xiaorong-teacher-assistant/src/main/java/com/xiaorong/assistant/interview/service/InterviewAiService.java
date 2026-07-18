package com.xiaorong.assistant.interview.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaorong.assistant.ai.dto.AiDtos.AiMessage;
import com.xiaorong.assistant.ai.dto.AiDtos.AiStructuredRequest;
import com.xiaorong.assistant.ai.dto.AiDtos.AiStructuredResponse;
import com.xiaorong.assistant.ai.prompt.StudyPromptTemplates;
import com.xiaorong.assistant.ai.service.AiGatewayService;
import com.xiaorong.assistant.interview.domain.InterviewCatalog;
import com.xiaorong.assistant.interview.domain.InterviewIntroductionFeedback;
import com.xiaorong.assistant.interview.domain.InterviewQuestion;
import com.xiaorong.assistant.interview.domain.InterviewQuestionContext;
import com.xiaorong.assistant.interview.domain.InterviewReport;
import com.xiaorong.assistant.interview.domain.InterviewScore;
import com.xiaorong.assistant.interview.persistence.InterviewStore.RecordData;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class InterviewAiService {
    private final AiGatewayService gateway;
    private final ObjectMapper objectMapper;
    private final InterviewCatalog catalog;

    public InterviewAiService(AiGatewayService gateway, ObjectMapper objectMapper, InterviewCatalog catalog) {
        this.gateway = gateway;
        this.objectMapper = objectMapper;
        this.catalog = catalog;
    }

    public InterviewIntroductionFeedback assessIntroduction(String positionName, String selfIntroduction) {
        InterviewIntroductionFeedback fallback = introductionFallback(selfIntroduction);
        try {
            String prompt = render(StudyPromptTemplates.INTERVIEWER_INTRODUCTION_FEEDBACK_TEMPLATE, Map.of(
                    "positionName", safe(positionName), "selfIntroduction", compact(selfIntroduction, 5000)));
            AiStructuredResponse response = gateway.structured(new AiStructuredRequest(
                    "interview-introduction-feedback", "InterviewIntroductionFeedbackResult", List.of(new AiMessage("system", prompt))));
            Map<String, Object> data = response.data();
            String summary = string(data == null ? null : data.get("summary"), fallback.summary());
            return new InterviewIntroductionFeedback(summary,
                    stringList(data == null ? null : data.get("strengths"), fallback.strengths()),
                    stringList(data == null ? null : data.get("gaps"), fallback.gaps()),
                    !Boolean.TRUE.equals(response.mock()), null, null);
        } catch (RuntimeException ex) {
            return fallback;
        }
    }

    public InterviewQuestion nextQuestion(InterviewQuestionContext context, int questionIndex) {
        InterviewQuestion fallback = fallbackQuestion(context, questionIndex);
        try {
            String prompt = render(StudyPromptTemplates.INTERVIEWER_FREE_QUESTION_TEMPLATE, Map.of(
                    "positionName", safe(context.positionName()),
                    "selfIntroduction", compact(context.selfIntroduction(), 5000),
                    "resumeText", compact(context.resumeText(), 6000),
                    "askedTopics", String.join("、", safeList(context.askedTopics())),
                    "questionIndex", String.valueOf(questionIndex + 1)));
            AiStructuredResponse response = gateway.structured(new AiStructuredRequest(
                    "interview-free-question", "InterviewQuestionResult", List.of(new AiMessage("system", prompt))));
            Map<String, Object> data = response.data();
            String topic = string(data == null ? null : data.get("topicName"), fallback.topicName());
            String keyHint = string(data == null ? null : data.get("keyHint"), fallback.keyHint());
            List<String> keywords = stringList(data == null ? null : data.get("keywords"), fallback.keywords());
            String answerSummary = string(data == null ? null : data.get("answerSummary"), fallback.answerSummary());
            return new InterviewQuestion(topic, keyHint, keywords, answerSummary, fallback.firstFollowUp(), fallback.secondFollowUp());
        } catch (RuntimeException ex) {
            return fallback;
        }
    }

    public InterviewScore score(InterviewQuestion question, String answer, List<String> followUpAnswers) {
        InterviewScore fallback = scoreByKeywords(question, answer, followUpAnswers);
        try {
            String prompt = render(StudyPromptTemplates.INTERVIEWER_SCORE_TEMPLATE, Map.of(
                    "topicName", question.topicName(),
                    "answerSummary", question.answerSummary(),
                    "keywords", String.join("、", question.keywords()),
                    "userAnswer", safe(answer),
                    "followUpAnswers", String.join("\n", safeList(followUpAnswers))));
            AiStructuredResponse response = gateway.structured(new AiStructuredRequest(
                    "interview-score", "InterviewScoreResult", List.of(new AiMessage("system", prompt))));
            Map<String, Object> data = response.data();
            Integer score = integer(data == null ? null : data.get("score"));
            if (score == null) return fallback;
            int normalizedScore = clamp(score);
            return new InterviewScore(normalizedScore, string(data.get("level"), level(normalizedScore)),
                    stringList(data.get("hitKeywords"), fallback.hitKeywords()),
                    stringList(data.get("missKeywords"), fallback.missKeywords()),
                    string(data.get("interviewerComment"), fallback.interviewerComment()),
                    string(data.get("risk"), fallback.risk()), !Boolean.TRUE.equals(response.mock()), null, null);
        } catch (RuntimeException ex) {
            return fallback;
        }
    }

    public InterviewReport review(List<RecordData> records, String positionName) {
        InterviewReport fallback = reviewByRecords(records, positionName);
        try {
            String prompt = render(StudyPromptTemplates.INTERVIEWER_REVIEW_TEMPLATE,
                    Map.of("answerRecords", objectMapper.writeValueAsString(records)));
            AiStructuredResponse response = gateway.structured(new AiStructuredRequest(
                    "interview-review", "InterviewReviewResult", List.of(new AiMessage("system", prompt))));
            Map<String, Object> data = response.data();
            Integer overall = integer(data == null ? null : data.get("overallScore"));
            if (overall == null) return fallback;
            int normalized = clamp(overall);
            return new InterviewReport(normalized, string(data.get("level"), level(normalized)),
                    stringList(data.get("strengthTags"), fallback.strengthTags()),
                    stringList(data.get("weakTags"), fallback.weakTags()),
                    string(data.get("summary"), fallback.summary()),
                    stringList(data.get("riskPoints"), fallback.riskPoints()),
                    stringList(data.get("suggestions"), fallback.suggestions()),
                    stringList(data.get("recommendedCourses"), fallback.recommendedCourses()),
                    !Boolean.TRUE.equals(response.mock()), null, null);
        } catch (JsonProcessingException | RuntimeException ex) {
            return fallback;
        }
    }

    private InterviewIntroductionFeedback introductionFallback(String introduction) {
        String text = safe(introduction);
        List<String> strengths = new ArrayList<>();
        List<String> gaps = new ArrayList<>();
        if (containsAny(text, "项目", "实习", "负责", "参与")) strengths.add("经历信息已覆盖");
        if (containsAny(text, "岗位", "应聘", "求职")) strengths.add("岗位意向明确");
        if (containsAny(text, "结果", "提升", "%", "降低", "增长")) strengths.add("有结果意识");
        if (strengths.isEmpty()) strengths.add("表达方向明确");
        if (!containsAny(text, "结果", "提升", "%", "降低", "增长")) gaps.add("补充可验证的结果或数据");
        if (!containsAny(text, "负责", "主导", "完成", "设计")) gaps.add("说明个人职责与贡献");
        return new InterviewIntroductionFeedback("介绍已记录。后续回答请优先说清个人贡献、技术取舍和结果。", strengths, gaps, false, null, null);
    }

    private InterviewQuestion fallbackQuestion(InterviewQuestionContext context, int questionIndex) {
        String intro = safe(context.selfIntroduction());
        if (containsAny(intro, "项目", "实习", "负责", "参与")) {
            return new InterviewQuestion("请结合你刚才提到的一段项目或实习经历，说明你承担的具体职责、关键取舍以及最终结果。",
                    "避免泛泛描述团队工作，重点说明你亲自做了什么、为什么这样做、如何验证效果。",
                    List.of("职责", "取舍", "结果"), "说明项目背景、个人贡献、技术取舍和可验证结果。",
                    "这项取舍为什么适合当时的场景？", "如果约束条件变化，你会如何调整方案？");
        }
        List<InterviewQuestion> catalogQuestions = catalog.questionsFor(context.positionName(), Math.max(1, questionIndex + 1));
        return catalogQuestions.get(Math.min(questionIndex, catalogQuestions.size() - 1));
    }

    private InterviewScore scoreByKeywords(InterviewQuestion question, String answer, List<String> followUps) {
        String allText = safe(answer) + " " + String.join(" ", safeList(followUps));
        String normalized = allText.toLowerCase();
        List<String> hits = question.keywords().stream().filter(keyword -> normalized.contains(keyword.toLowerCase())).toList();
        List<String> misses = question.keywords().stream().filter(keyword -> !hits.contains(keyword)).toList();
        int keywordScore = question.keywords().isEmpty() ? 0 : (int) Math.round(hits.size() * 70.0 / question.keywords().size());
        int expressionScore = Math.min(20, Math.max(4, allText.length() / 12));
        int followUpScore = Math.min(10, safeList(followUps).size() * 5);
        int score = clamp(keywordScore + expressionScore + followUpScore);
        String risk = misses.isEmpty() ? "回答覆盖了本题关键点，可以继续加强案例细节。" : "建议补充关键点：" + String.join("、", misses);
        String comment = score >= 75 ? "回答结构完整，关键点覆盖较好。" : "回答有基础思路，但请补充原理、取舍或实际案例。";
        return new InterviewScore(score, level(score), hits, misses, comment, risk, false, null, null);
    }

    private InterviewReport reviewByRecords(List<RecordData> records, String positionName) {
        List<RecordData> safeRecords = records == null ? List.of() : records;
        int overall = safeRecords.isEmpty() ? 0 : (int) Math.round(safeRecords.stream().map(RecordData::score)
                .filter(Objects::nonNull).mapToInt(Integer::intValue).average().orElse(0));
        List<String> weakTags = safeRecords.stream().flatMap(record -> safeList(record.missKeywords()).stream())
                .distinct().limit(5).toList();
        List<String> strengthTags = safeRecords.stream().flatMap(record -> safeList(record.hitKeywords()).stream())
                .distinct().limit(5).toList();
        List<String> risks = safeRecords.stream().map(RecordData::risk).filter(value -> value != null && !value.isBlank())
                .distinct().limit(3).toList();
        String summary = overall >= 75
                ? "整体回答较为扎实，已经具备岗位面试的基础竞争力。"
                : "已建立基础答题框架，建议围绕薄弱知识点补充原理与项目案例。";
        return new InterviewReport(overall, level(overall), strengthTags, weakTags, summary, risks,
                List.of("按 STAR 结构复盘项目案例并量化结果", "为每个知识点准备原理、取舍和实践三个层次", "针对薄弱标签完成一次限时复述"),
                List.of(safe(positionName) + "专项题库", "面试表达与项目复盘课程"), false, null, null);
    }

    private String render(String template, Map<String, String> values) {
        String result = template.replace("{INTERVIEWER_SYSTEM_RULE}", StudyPromptTemplates.INTERVIEWER_SYSTEM_RULE)
                .replace("{COMMON_STUDY_ROLE_RULE}", StudyPromptTemplates.COMMON_STUDY_ROLE_RULE)
                .replace("{PERSONA_INTERVIEWER_LANCHUAN}", StudyPromptTemplates.PERSONA_INTERVIEWER_LANCHUAN);
        for (Map.Entry<String, String> entry : values.entrySet()) result = result.replace("{" + entry.getKey() + "}", safe(entry.getValue()));
        return result;
    }
    private boolean containsAny(String text, String... values) {
        for (String value : values) if (text.contains(value)) return true;
        return false;
    }
    private Integer integer(Object value) {
        if (value instanceof Number number) return number.intValue();
        if (value instanceof String text) try { return Integer.parseInt(text); } catch (NumberFormatException ignored) { }
        return null;
    }
    private String string(Object value, String fallback) { return value instanceof String text && !text.isBlank() ? text : fallback; }
    private List<String> stringList(Object value, List<String> fallback) {
        if (!(value instanceof List<?> values)) return fallback;
        List<String> result = new ArrayList<>();
        for (Object item : values) if (item != null && !item.toString().isBlank()) result.add(item.toString().trim());
        return result;
    }
    private List<String> safeList(List<String> values) { return values == null ? List.of() : values.stream().filter(value -> value != null && !value.isBlank()).toList(); }
    private String compact(String value, int maxLength) { String safe = safe(value); return safe.length() <= maxLength ? safe : safe.substring(0, maxLength); }
    private String safe(String value) { return value == null ? "" : value; }
    private int clamp(int score) { return Math.max(0, Math.min(100, score)); }
    private String level(int score) { return score >= 85 ? "优秀" : score >= 70 ? "良好" : score >= 55 ? "及格" : "待提升"; }
}