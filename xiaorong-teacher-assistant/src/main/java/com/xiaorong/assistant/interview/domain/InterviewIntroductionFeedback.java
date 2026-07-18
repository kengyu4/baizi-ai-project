package com.xiaorong.assistant.interview.domain;

import java.util.List;

/** Feedback returned after the candidate's opening self-introduction. */
public record InterviewIntroductionFeedback(String summary, List<String> strengths, List<String> gaps,
                                            boolean aiGenerated, String providerCode, String model) { }