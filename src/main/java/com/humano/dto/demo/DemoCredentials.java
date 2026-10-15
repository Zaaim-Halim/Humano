package com.humano.dto.demo;

import java.util.List;

/**
 * How to sign in to a demo workspace: its workspace (subdomain), the admin login and the persona
 * logins, all sharing one generated password. Shown once — the password is not stored.
 */
public record DemoCredentials(String workspace, String adminLogin, List<String> personaLogins, String password) {}
