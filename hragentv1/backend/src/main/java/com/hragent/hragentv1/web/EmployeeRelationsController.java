package com.hragent.hragentv1.web;

import com.hragent.hragentv1.dto.ApiResponse;
import com.hragent.hragentv1.dto.EmployeeRelationsDtos.*;
import com.hragent.hragentv1.service.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/employee-relations")
public class EmployeeRelationsController {
    private final AuthService auth;
    private final EmployeeRelationsService service;
    public EmployeeRelationsController(AuthService auth, EmployeeRelationsService service) { this.auth=auth; this.service=service; }
    @GetMapping("/resources") public ApiResponse<?> resources(HttpServletRequest r) { return ApiResponse.ok(service.resources(auth.requireUser(r))); }
    @GetMapping("/guides") public ApiResponse<?> guides(HttpServletRequest r) { auth.requireUser(r); return ApiResponse.ok(service.guides()); }
    @GetMapping("/journey") public ApiResponse<?> journey(HttpServletRequest r) { return ApiResponse.ok(service.journey(auth.requireUser(r))); }
    @PutMapping("/journey/{key}") public ApiResponse<?> task(HttpServletRequest r,@PathVariable String key,@Valid @RequestBody Toggle input) { return ApiResponse.ok(service.setTask(auth.requireUser(r),key,input.enabled())); }
    @PutMapping("/analytics-consent") public ApiResponse<?> consent(HttpServletRequest r,@Valid @RequestBody Toggle input) { return ApiResponse.ok(service.consent(auth.requireUser(r),input.enabled())); }
    @PostMapping("/cases") public ApiResponse<?> submit(HttpServletRequest r,@Valid @RequestBody CaseInput input) { return ApiResponse.ok(service.submit(auth.requireUser(r),input)); }
    @GetMapping("/cases") public ApiResponse<?> mine(HttpServletRequest r) { return ApiResponse.ok(service.mine(auth.requireUser(r))); }
    @PostMapping("/cases/lookup") public ApiResponse<?> lookup(HttpServletRequest r,@Valid @RequestBody Lookup input) { return ApiResponse.ok(service.lookup(auth.requireUser(r),input.receipt())); }
    @PostMapping("/cases/{id}/followup") public ApiResponse<?> followup(HttpServletRequest r,@PathVariable Long id,@Valid @RequestBody Followup input) { return ApiResponse.ok(service.followup(auth.requireUser(r),id,input)); }
    @PostMapping("/feedback") public ApiResponse<?> feedback(HttpServletRequest r,@Valid @RequestBody Feedback input) {
        var user=auth.requireUser(r);
        if (ErSafety.sensitive(input.question())) return ApiResponse.ok("为保护隐私，这类内容不纳入知识主题统计。需要支持时可使用关怀资源或合规申诉入口。");
        service.observe(user,input.question(),true); return ApiResponse.ok("已加入 HR 知识维护待办，不保存问题原文");
    }
    @GetMapping("/hr/settings") public ApiResponse<?> settings(HttpServletRequest r) { return ApiResponse.ok(service.settings(auth.requireUser(r))); }
    @PutMapping("/hr/settings") public ApiResponse<?> settings(HttpServletRequest r,@Valid @RequestBody SettingsInput input) { return ApiResponse.ok(service.saveSettings(auth.requireUser(r),input)); }
    @GetMapping("/hr/cases") public ApiResponse<?> queue(HttpServletRequest r) { return ApiResponse.ok(service.queue(auth.requireUser(r))); }
    @GetMapping("/hr/cases/{id}") public ApiResponse<?> detail(HttpServletRequest r,@PathVariable Long id) { return ApiResponse.ok(service.detail(auth.requireUser(r),id)); }
    @PatchMapping("/hr/cases/{id}") public ApiResponse<?> process(HttpServletRequest r,@PathVariable Long id,@Valid @RequestBody CaseAction input) { return ApiResponse.ok(service.process(auth.requireUser(r),id,input)); }
    @GetMapping("/hr/insights") public ApiResponse<?> insights(HttpServletRequest r) { return ApiResponse.ok(service.insights(auth.requireUser(r))); }
    @GetMapping("/hr/gaps") public ApiResponse<?> gaps(HttpServletRequest r) { return ApiResponse.ok(service.gaps(auth.requireUser(r))); }
    @PatchMapping("/hr/gaps/{id}") public ApiResponse<?> gap(HttpServletRequest r,@PathVariable Long id,@Valid @RequestBody GapAction input) { service.gapStatus(auth.requireUser(r),id,input.status()); return ApiResponse.ok("知识待办已更新"); }
    @PostMapping("/hr/knowledge-drafts") public ApiResponse<?> seed(HttpServletRequest r) { return ApiResponse.ok(service.seedDrafts(auth.requireUser(r))); }
}
