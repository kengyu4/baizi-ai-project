package com.xiaorong.assistant.interview.service;

import com.xiaorong.assistant.interview.dto.InterviewDtos.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface InterviewService {
    List<InterviewerResponse> listInterviewers();
    List<ResumeResponse> listResumes();
    ResumeResponse createResume(ResumeUpsertRequest request);
    ResumeResponse updateResume(Long resumeId, ResumeUpsertRequest request);
    ResumeUploadResponse parseResume(MultipartFile file, String positionName);
    InterviewSessionResponse createSession(CreateInterviewSessionRequest request);
    InterviewSessionResponse getSession(Long sessionId);
    InterviewSessionResponse submitSelfIntroduction(Long sessionId, SubmitSelfIntroductionRequest request);
    SubmitInterviewAnswerResponse submitAnswer(Long sessionId, SubmitInterviewAnswerRequest request);
    InterviewReportResponse getReport(Long sessionId);
    List<InterviewRecordListItem> listRecords();
    List<InterviewRecordResponse> getRecords(Long sessionId);
}
