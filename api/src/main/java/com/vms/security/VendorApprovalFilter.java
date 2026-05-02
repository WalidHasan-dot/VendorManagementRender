package com.vms.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vms.entity.Status;
import com.vms.repository.VendorRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Blocks vendor users from accessing the system until their vendor profile is APPROVED.
 * Vendors with status PENDING or REJECTED receive 403.
 */
@Component
public class VendorApprovalFilter extends OncePerRequestFilter {

    @Autowired
    private VendorRepository vendorRepository;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path != null && path.startsWith("/api/auth/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getPrincipal() == null) {
            filterChain.doFilter(request, response);
            return;
        }
        if (!(auth.getPrincipal() instanceof UserDetailsImpl)) {
            filterChain.doFilter(request, response);
            return;
        }
        boolean isVendor = auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_VENDOR".equals(a.getAuthority()));
        if (!isVendor) {
            filterChain.doFilter(request, response);
            return;
        }
        UserDetailsImpl userDetails = (UserDetailsImpl) auth.getPrincipal();
        var vendorOpt = vendorRepository.findByUserId(userDetails.getId());
        if (vendorOpt.isEmpty()) {
            filterChain.doFilter(request, response);
            return;
        }
        Status status = vendorOpt.get().getStatus();
        if (status == Status.APPROVED) {
            filterChain.doFilter(request, response);
            return;
        }
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json");
        Map<String, Object> body = new HashMap<>();
        body.put("success", false);
        body.put("message", status == Status.PENDING
                ? "Your vendor application is pending approval. Please wait for admin approval."
                : "Your vendor application was rejected. Contact admin for assistance.");
        new ObjectMapper().writeValue(response.getOutputStream(), body);
    }
}
