package com.xiaorong.assistant.interview.domain;

import java.util.List;

public record InterviewScore(int score, String level, List<String> hitKeywords, List<String> missKeywords,
                             String interviewerComment, String risk, boolean aiGenerated,
                             String providerCode, String model) { }
