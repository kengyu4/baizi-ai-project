package com.xiaorong.assistant.interview.controller;

import com.xiaorong.assistant.common.Result;
import com.xiaorong.assistant.interview.dto.InterviewDtos.*;
import com.xiaorong.assistant.interview.service.InterviewService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/interviews")
public class InterviewController {
    private final InterviewService service;
    public InterviewController(InterviewService service) { this.service = service; }
    @GetMapping("/interviewers") public Result<List<InterviewerResponse>> listInterviewers(){return Result.success(service.listInterviewers());}
    @GetMapping("/resumes") public Result<List<ResumeResponse>> listResumes(){return Result.success(service.listResumes());}
    @PostMapping("/resumes") public Result<ResumeResponse> createResume(@Valid @RequestBody ResumeUpsertRequest request){return Result.success(service.createResume(request));}
    @PutMapping("/resumes/{resumeId}") public Result<ResumeResponse> updateResume(@PathVariable Long resumeId,@Valid @RequestBody ResumeUpsertRequest request){return Result.success(service.updateResume(resumeId,request));}
    @PostMapping(value="/resumes/upload",consumes=MediaType.MULTIPART_FORM_DATA_VALUE) public Result<ResumeUploadResponse> uploadResume(@RequestPart("file") MultipartFile file,@RequestParam(required=false) String positionName){return Result.success(service.parseResume(file,positionName));}
    @PostMapping("/sessions") public Result<InterviewSessionResponse> createSession(@Valid @RequestBody CreateInterviewSessionRequest request){return Result.success(service.createSession(request));}
    @GetMapping("/sessions/{sessionId}") public Result<InterviewSessionResponse> getSession(@PathVariable Long sessionId){return Result.success(service.getSession(sessionId));}
    @PostMapping("/sessions/{sessionId}/self-introduction") public Result<InterviewSessionResponse> submitSelfIntroduction(@PathVariable Long sessionId,@Valid @RequestBody SubmitSelfIntroductionRequest request){return Result.success(service.submitSelfIntroduction(sessionId,request));}
    @PostMapping("/sessions/{sessionId}/answers") public Result<SubmitInterviewAnswerResponse> submitAnswer(@PathVariable Long sessionId,@Valid @RequestBody SubmitInterviewAnswerRequest request){return Result.success(service.submitAnswer(sessionId,request));}
    @GetMapping("/sessions/{sessionId}/report") public Result<InterviewReportResponse> report(@PathVariable Long sessionId){return Result.success(service.getReport(sessionId));}
    @GetMapping("/records") public Result<List<InterviewRecordListItem>> listRecords(){return Result.success(service.listRecords());}
    @GetMapping("/records/{sessionId}") public Result<List<InterviewRecordResponse>> records(@PathVariable Long sessionId){return Result.success(service.getRecords(sessionId));}
}
