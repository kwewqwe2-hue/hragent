package com.hragent.hragentv1.web;

import com.hragent.hragentv1.dto.ApiResponse;
import com.hragent.hragentv1.dto.WorkspaceDtos;
import com.hragent.hragentv1.service.WorkspaceService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/workspaces")
public class WorkspaceController {
    private final WorkspaceService workspaceService;

    public WorkspaceController(WorkspaceService workspaceService) {
        this.workspaceService = workspaceService;
    }

    @GetMapping("/mine")
    public ApiResponse<List<WorkspaceDtos.WorkspaceSummary>> mine(HttpServletRequest request) {
        return ApiResponse.ok(workspaceService.mine(request));
    }

    @PostMapping
    public ApiResponse<WorkspaceDtos.WorkspaceSummary> create(
            HttpServletRequest servletRequest,
            @Valid @RequestBody WorkspaceDtos.CreateWorkspaceRequest request
    ) {
        return ApiResponse.ok(workspaceService.create(servletRequest, request));
    }

    @PostMapping("/join")
    public ApiResponse<WorkspaceDtos.WorkspaceSummary> join(
            HttpServletRequest servletRequest,
            @Valid @RequestBody WorkspaceDtos.JoinWorkspaceRequest request
    ) {
        return ApiResponse.ok(workspaceService.join(servletRequest, request));
    }

    @PostMapping("/{workspaceId}/leave")
    public ApiResponse<WorkspaceDtos.WorkspaceSummary> leave(
            HttpServletRequest request,
            @PathVariable Long workspaceId
    ) {
        return ApiResponse.ok("已退出企业空间", workspaceService.leave(request, workspaceId));
    }

    @GetMapping("/{workspaceId}/members")
    public ApiResponse<List<WorkspaceDtos.MemberView>> members(
            HttpServletRequest request,
            @PathVariable Long workspaceId
    ) {
        return ApiResponse.ok(workspaceService.members(request, workspaceId));
    }

    @GetMapping("/{workspaceId}/join-requests")
    public ApiResponse<List<WorkspaceDtos.MemberView>> joinRequests(
            HttpServletRequest request,
            @PathVariable Long workspaceId
    ) {
        return ApiResponse.ok(workspaceService.joinRequests(request, workspaceId));
    }

    @PostMapping("/{workspaceId}/join-requests/{membershipId}/review")
    public ApiResponse<WorkspaceDtos.MemberView> review(
            HttpServletRequest servletRequest,
            @PathVariable Long workspaceId,
            @PathVariable Long membershipId,
            @Valid @RequestBody WorkspaceDtos.ReviewRequest request
    ) {
        return ApiResponse.ok(workspaceService.review(servletRequest, workspaceId, membershipId, request));
    }

    @PutMapping("/{workspaceId}/members/{membershipId}/role")
    public ApiResponse<WorkspaceDtos.MemberView> updateRole(
            HttpServletRequest servletRequest,
            @PathVariable Long workspaceId,
            @PathVariable Long membershipId,
            @Valid @RequestBody WorkspaceDtos.RoleUpdateRequest request
    ) {
        return ApiResponse.ok(workspaceService.updateRole(servletRequest, workspaceId, membershipId, request));
    }
}
