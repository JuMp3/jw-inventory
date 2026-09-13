package com.gp.jwinventory.dto;

public record CountResult(Long publicationId, Integer counted, int expected, Integer difference) {
}
