package com.humano.dto.demo;

/** A demo workspace just set up (or its setup retried), with its sign-in details. */
public record DemoWorkspaceCreated(DemoWorkspaceResponse demo, DemoCredentials credentials) {}
