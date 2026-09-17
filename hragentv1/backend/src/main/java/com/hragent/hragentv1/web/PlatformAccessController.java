package com.hragent.hragentv1.web;
import com.hragent.hragentv1.dto.*;
import com.hragent.hragentv1.service.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController @RequestMapping("/platform-access") public class PlatformAccessController {
 private final AuthService auth; private final PlatformAccessService service;
 public PlatformAccessController(AuthService a,PlatformAccessService s){auth=a;service=s;}
 @PostMapping("/requests") public ApiResponse<PlatformAccessDtos.View> create(HttpServletRequest r,@Valid @RequestBody PlatformAccessDtos.CreateRequest b){return ApiResponse.ok(service.create(auth.requireUser(r),b));}
 @GetMapping("/mine") public ApiResponse<List<PlatformAccessDtos.View>> mine(HttpServletRequest r){return ApiResponse.ok(service.mine(auth.requireUser(r)));}
 @GetMapping("/manager") public ApiResponse<List<PlatformAccessDtos.View>> manager(HttpServletRequest r){return ApiResponse.ok(service.manager(auth.requireUser(r)));}
 @PutMapping("/manager/{id}/review") public ApiResponse<PlatformAccessDtos.View> managerReview(HttpServletRequest r,@PathVariable Long id,@Valid @RequestBody PlatformAccessDtos.ReviewRequest b){return ApiResponse.ok(service.managerReview(auth.requireUser(r),id,b));}
 @GetMapping("/hr/pending") public ApiResponse<List<PlatformAccessDtos.View>> pending(HttpServletRequest r){return ApiResponse.ok(service.pending(auth.requireUser(r)));}
 @PutMapping("/hr/{id}/review") public ApiResponse<PlatformAccessDtos.View> review(HttpServletRequest r,@PathVariable Long id,@Valid @RequestBody PlatformAccessDtos.ReviewRequest b){return ApiResponse.ok(service.review(auth.requireUser(r),id,b));}
}
