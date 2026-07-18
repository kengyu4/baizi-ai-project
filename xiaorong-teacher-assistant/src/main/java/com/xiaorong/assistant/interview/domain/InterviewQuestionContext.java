package com.xiaorong.assistant.interview.domain;

import java.util.List;

/** Persisted evidence used to generate one concrete, non-fabricated interview question at a time. */
public record InterviewQuestionContext(String positionName, String resumeText, String selfIntroduction,
                                       List<String> askedTopics) { }