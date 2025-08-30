package com.humano.security;

public enum AuthenticationType {
    DATABASE,      // Standard username/password authentication
    LDAP,          // LDAP authentication
    OAUTH2,        // OAuth2 authentication (Google, Facebook, etc.)
    SAML,          // SAML authentication
    JWT,           // JSON Web Token authentication
    API_KEY,       // API Key based authentication
    FORM_LOGIN,    // Form-based login authentication
    HTTP_BASIC     // HTTP Basic authentication
}
