package com.schedulix.engine;

// owner is either a teacher id or a place name; lecture is 1-based.
public record BusyCell(String owner, int day, int lecture) {
}