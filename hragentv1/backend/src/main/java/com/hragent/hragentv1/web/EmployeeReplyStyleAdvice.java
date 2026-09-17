package com.hragent.hragentv1.web;

import com.hragent.hragentv1.dto.ApiResponse;
import com.hragent.hragentv1.dto.WebChatDtos.MessageResponse;
import com.hragent.hragentv1.service.EmployeeReplyStyle;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

@RestControllerAdvice
public class EmployeeReplyStyleAdvice implements ResponseBodyAdvice<Object> {
 public boolean supports(MethodParameter method,Class<? extends HttpMessageConverter<?>> converter){return true;}
 public Object beforeBodyWrite(Object body,MethodParameter method,MediaType contentType,Class<? extends HttpMessageConverter<?>> converter,ServerHttpRequest request,ServerHttpResponse response){
  if(body instanceof ApiResponse<?> envelope&&envelope.data() instanceof MessageResponse reply)
   return new ApiResponse<>(envelope.success(),envelope.message(),EmployeeReplyStyle.soften(reply));
  return body;
 }
}
