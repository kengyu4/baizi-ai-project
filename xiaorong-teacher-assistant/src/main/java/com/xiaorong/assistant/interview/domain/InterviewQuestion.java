package com.xiaorong.assistant.interview.domain;

import java.util.List;

public record InterviewQuestion(String topicName, String keyHint, List<String> keywords,
                                String answerSummary, String firstFollowUp, String secondFollowUp) { }
