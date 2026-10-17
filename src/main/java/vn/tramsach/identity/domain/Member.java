package vn.tramsach.identity.domain;

import java.util.UUID;

public record Member(UUID id, String email, String name, String passwordHash, String role) {}
