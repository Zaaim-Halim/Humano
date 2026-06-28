package com.humano.dto.hr.requests;

import java.util.UUID;

/**
 * DTO record for partially updating a EmployeeSignature record. Null fields are left unchanged.
 */
public record UpdateEmployeeSignatureRequest(UUID signatureFileId, String certificate) {}
