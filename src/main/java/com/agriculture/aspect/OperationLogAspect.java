package com.agriculture.aspect;

import com.agriculture.entity.OperationLog;
import com.agriculture.service.OperationLogService;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;

@Aspect
@Component
public class OperationLogAspect {

    private final OperationLogService operationLogService;

    public OperationLogAspect(OperationLogService operationLogService) {
        this.operationLogService = operationLogService;
    }

    @Around("@annotation(operation)")
    public Object record(ProceedingJoinPoint joinPoint, OperationLogRecord operation) throws Throwable {
        LocalDateTime now = LocalDateTime.now();
        try {
            Object result = joinPoint.proceed();
            saveLog(operation, "SUCCESS", null, now);
            return result;
        } catch (Throwable e) {
            saveLog(operation, "FAILED", e.getMessage(), now);
            throw e;
        }
    }

    private void saveLog(OperationLogRecord operation, String result, String errorMessage, LocalDateTime createTime) {
        try {
            OperationLog log = new OperationLog();
            log.setOperatorName(resolveOperatorName());
            log.setOperationType(operation.type());
            log.setTarget(operation.target());
            log.setDetail(operation.detail());
            log.setResult(result);
            log.setErrorMessage(errorMessage);
            log.setCreateTime(createTime);

            ServletRequestAttributes attributes =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                log.setRequestMethod(request.getMethod());
                log.setRequestUri(request.getRequestURI());
                log.setIp(resolveClientIp(request));
            }

            operationLogService.save(log);
        } catch (Exception ignored) {
            // Operation logging must never affect the real business request.
        }
    }

    private String resolveOperatorName() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null) {
            return "anonymous";
        }
        return authentication.getName();
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
