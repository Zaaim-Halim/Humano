package com.humano.service.hr.dto.requests;

/**
 * DTO record for updating an existing employee document.
 * Contains fields that can be updated for a document.
 */
public record UpdateEmployeeDocumentRequest(
    String type
) {
}
