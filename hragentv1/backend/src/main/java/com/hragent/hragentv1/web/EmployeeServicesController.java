package com.hragent.hragentv1.web;

import com.hragent.hragentv1.domain.*;
import com.hragent.hragentv1.dto.ApiResponse;
import com.hragent.hragentv1.dto.EmployeeServiceDtos.*;
import com.hragent.hragentv1.service.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/employee-services")
public class EmployeeServicesController {
    private final AuthService auth;
    private final EmployeeServiceProfileService profiles;
    private final PolicyCopilotService policies;
    private final EmployeeReminderService reminders;
    private final EmployeeChecklistService checklists;
    private final EmployeeRelationsService relations;
    public EmployeeServicesController(AuthService auth, EmployeeServiceProfileService profiles,
            PolicyCopilotService policies, EmployeeReminderService reminders, EmployeeChecklistService checklists, EmployeeRelationsService relations) {
        this.auth = auth; this.profiles = profiles; this.policies = policies; this.reminders = reminders; this.checklists = checklists;
        this.relations = relations;
    }
    @GetMapping("/profile")
    public ApiResponse<ProfileView> profile(HttpServletRequest request) {
        return ApiResponse.ok(profiles.profile(auth.requireUser(request)));
    }
    @GetMapping("/hr/profiles/{employeeId}")
    public ApiResponse<ProfileView> employeeProfile(HttpServletRequest request, @PathVariable Long employeeId) {
        return ApiResponse.ok(profiles.profile(profiles.requireEmployee(auth.requireUser(request), employeeId)));
    }
    @PutMapping("/hr/profiles/{employeeId}")
    public ApiResponse<ProfileView> updateProfile(HttpServletRequest request, @PathVariable Long employeeId,
            @Valid @RequestBody ProfileRequest input) {
        return ApiResponse.ok(profiles.update(auth.requireUser(request), employeeId, input));
    }
    @PostMapping("/policy/ask")
    public ApiResponse<PolicyAnswer> policy(HttpServletRequest request, @Valid @RequestBody Question input) {
        var user = auth.requireUser(request);
        var safety = relations.triage(user, input.message());
        if (safety.isPresent()) return ApiResponse.ok(new PolicyAnswer(safety.get(), "HUMAN_SUPPORT", profiles.profile(user), List.of(), List.of()));
        var support=relations.supportReply(user,input.message());
        if (support.isPresent()) return ApiResponse.ok(new PolicyAnswer(support.get(), "GUIDANCE", profiles.profile(user), List.of(), List.of()));
        var guide = relations.guideReply(user, input.message());
        var result = policies.answer(user, input.message());
        if (SuppliedPolicySearch.isDocument(result)) {
            relations.observe(user,input.message(),"DOCUMENT_REVIEW".equals(result.status()));
            return ApiResponse.ok(result);
        }
        relations.observe(user, input.message(), guide.isEmpty() && !"MATCHED".equals(result.status()));
        if (guide.isPresent()) return ApiResponse.ok(new PolicyAnswer(guide.get() + ("MATCHED".equals(result.status()) ? "\n\n企业适用依据：\n" + result.answer() : ""), "GUIDANCE", result.profile(), result.citations(), result.gaps()));
        return ApiResponse.ok(result);
    }
    @GetMapping("/policy/documents")
    public ApiResponse<List<java.util.Map<String,Object>>> documents(HttpServletRequest request) {
        return ApiResponse.ok(policies.suppliedDocuments(auth.requireUser(request)));
    }
    @GetMapping("/reminders")
    public ApiResponse<List<EmployeeServiceTask>> reminders(HttpServletRequest request) {
        return ApiResponse.ok(reminders.mine(auth.requireUser(request)));
    }
    @PatchMapping("/reminders/{id}")
    public ApiResponse<EmployeeServiceTask> task(HttpServletRequest request, @PathVariable Long id,
            @Valid @RequestBody TaskAction input) {
        return ApiResponse.ok(reminders.update(auth.requireUser(request), id, input));
    }
    @GetMapping("/reminders/calendar")
    public ResponseEntity<byte[]> calendar(HttpServletRequest request) {
        byte[] content = reminders.calendar(auth.requireUser(request)).getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=hragent-reminders.ics")
                .contentType(MediaType.parseMediaType("text/calendar;charset=UTF-8")).body(content);
    }
    @PostMapping("/checklists")
    public ApiResponse<Checklist> checklist(HttpServletRequest request, @Valid @RequestBody ChecklistRequest input) {
        return ApiResponse.ok(checklists.generate(auth.requireUser(request), input));
    }
}
