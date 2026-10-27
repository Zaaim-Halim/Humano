package com.humano.dto.hr.workflow.requests;

import jakarta.validation.constraints.Size;

/**
 * Notes from the feedback meeting in which a review's outcome was shared with the employee.
 */
public record FeedbackMeetingRequest(@Size(max = 10000) String notes) {}
