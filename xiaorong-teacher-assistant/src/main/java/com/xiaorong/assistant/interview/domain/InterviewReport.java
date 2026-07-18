package com.xiaorong.assistant.interview.domain;

import java.util.List;

public record InterviewReport(int overallScore, String level, List<String> strengthTags, List<String> weakTags,
                              String summary, List<String> riskPoints, List<String> suggestions,
                              List<String> recommendedCourses, boolean aiGenerated,
                              String providerCode, String model) { }
