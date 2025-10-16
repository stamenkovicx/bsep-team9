package com.bsep.pki_system.jwt;

import com.bsep.pki_system.model.UserRole;

public class UserPrincipal {
    private Long id;
    private String email;
    private UserRole role;

    public UserPrincipal(Long id, String email, UserRole role) {
        this.id = id;
        this.email = email;
        this.role = role;
    }

    public Long getId() { return id; }
    public String getEmail() { return email; }
    public UserRole getRole() { return role; }
}
