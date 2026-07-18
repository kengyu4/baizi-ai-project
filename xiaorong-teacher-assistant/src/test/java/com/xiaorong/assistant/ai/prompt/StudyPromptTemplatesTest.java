package com.xiaorong.assistant.ai.prompt;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class StudyPromptTemplatesTest {

    @Test
    void previewContainsLanchuanIdentityFreeQuestioningAndFollowUpTemplates() {
        Map<String, String> prompts = StudyPromptTemplates.preview();

        assertThat(prompts)
                .containsKeys(
                        "INTERVIEWER_INTRODUCTION_FEEDBACK_TEMPLATE",
                        "INTERVIEWER_FREE_QUESTION_TEMPLATE",
                        "INTERVIEWER_FOLLOW_UP_TEMPLATE",
                        "INTERVIEWER_SCORE_TEMPLATE",
                        "INTERVIEWER_REVIEW_TEMPLATE");
        assertThat(prompts.get("INTERVIEWER_FREE_QUESTION_TEMPLATE"))
                .contains("{INTERVIEWER_SYSTEM_RULE}")
                .contains("每次只问一个具体问题")
                .contains("不虚构项目、公司、职责或结果");
        assertThat(prompts.get("INTERVIEWER_FOLLOW_UP_TEMPLATE"))
                .contains("最大追问层数为 2");
        assertThat(prompts.get("INTERVIEWER_SCORE_TEMPLATE"))
                .contains("\"score\"")
                .contains("\"risk\"");
        assertThat(prompts.get("INTERVIEWER_REVIEW_TEMPLATE"))
                .contains("\"overallScore\"")
                .contains("\"recommendedCourses\"");
    }
}
