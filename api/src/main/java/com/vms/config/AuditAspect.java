package com.vms.config;

import com.vms.service.AuditService;
import com.vms.security.UserDetailsImpl;
import com.vms.entity.User;
import com.vms.repository.UserRepository;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class AuditAspect {

    @Autowired
    private AuditService auditService;

    @Autowired
    private UserRepository userRepository;

    @AfterReturning(pointcut = "execution(* com.vms.controller.AuthController.registerUser(..))", returning = "result")
    public void logUserCreation(JoinPoint joinPoint, Object result) {
        logAction("USER_CREATION", "A new user was created via manual registration");
    }

    @AfterReturning(pointcut = "execution(* com.vms.controller.PaymentController.createPayment(..))", returning = "result")
    public void logPaymentApproval(JoinPoint joinPoint, Object result) {
        logAction("PAYMENT_CREATION", "A new payment was recorded");
    }

    private void logAction(String action, String details) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        User currentUser = null;
        if (auth != null && auth.getPrincipal() instanceof UserDetailsImpl) {
            UserDetailsImpl userDetails = (UserDetailsImpl) auth.getPrincipal();
            currentUser = userRepository.findById(userDetails.getId()).orElse(null);
        }
        auditService.log(currentUser, action, details);
    }
}
