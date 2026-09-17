package com.hragent.hragentv1.web;
import com.hragent.hragentv1.dto.ApiResponse;
import com.hragent.hragentv1.service.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
@RestController
@RequestMapping("/employee-relations/pulse")
public class EmployeePulseController {
    private final AuthService auth;private final EmployeePulseService service;
    public EmployeePulseController(AuthService auth,EmployeePulseService service){this.auth=auth;this.service=service;}
    public record Submission(String period,boolean consent,Map<String,Integer> answers){}
    @GetMapping public ApiResponse<?> current(HttpServletRequest r){return ApiResponse.ok(service.current(auth.requireUser(r)));}
    @PostMapping public ApiResponse<?> submit(HttpServletRequest r,@RequestBody Submission input){service.submit(auth.requireUser(r),input.period(),input.consent(),input.answers());return ApiResponse.ok("感谢你的反馈，已保存。");}
    @GetMapping("/results") public ApiResponse<?> results(HttpServletRequest r,@RequestParam String period){return ApiResponse.ok(service.results(auth.requireUser(r),period));}
}
