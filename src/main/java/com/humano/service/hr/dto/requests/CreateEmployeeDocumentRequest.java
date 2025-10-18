package com.humano.service.hr.dto.requests;

/**
 * DTO record for creating a new employee document.
 * Contains the document type information.
 */
public record CreateEmployeeDocumentRequest(
    String type
) {
}
