package com.vms.dto;

import lombok.Data;
import java.util.List;
import java.util.UUID;

@Data
public class JwtResponse {
    private String token;
    private String type = "Bearer";
    private UUID id;
    private String username;
    private String email;
    private List<String> roles;
    private String refreshToken;
    private UUID vendorId;

    public JwtResponse(String accessToken, String refreshToken, UUID id, String username, String email,
            UUID vendorId, List<String> roles) {
        this.token = accessToken;
        this.refreshToken = refreshToken;
        this.id = id;
        this.username = username;
        this.email = email;
        this.vendorId = vendorId;
        this.roles = roles;
    }
}
