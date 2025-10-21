package com.delivery.models;

import com.delivery.security.SecurityLevel;

public class User {
    private int id;
    private String username;
    private String role;
    private SecurityLevel clearance;

    public User(int id, String username, String role, SecurityLevel clearance) {
        this.id = id;
        this.username = username;
        this.role = role;
        this.clearance = clearance;
    }

    public int getId() {
    	return id;
    	}
    public String getUsername() { 
    	return username;
    	}
    public String getRole() { 
    	return role;
    	}
    public SecurityLevel getClearance() { 
    	return clearance;
    	}
}
