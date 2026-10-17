package vn.tramsach.catalog.domain;

import java.util.UUID;

public record Category(UUID id, String name, String slug, boolean active) {}
