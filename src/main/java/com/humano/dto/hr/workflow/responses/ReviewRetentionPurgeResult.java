package com.humano.dto.hr.workflow.responses;

/**
 * What a retention run deleted: whole cycles and the reviews in them. {@code failed} cycles were left
 * as they were (see the log) and are tried again on the next run.
 */
public record ReviewRetentionPurgeResult(int cycles, long reviews, int failed) {}
