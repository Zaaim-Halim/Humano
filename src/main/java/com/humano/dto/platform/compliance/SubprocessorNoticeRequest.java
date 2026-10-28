package com.humano.dto.platform.compliance;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/** A new sub-processor to announce to every customer, and the day it starts processing their data. */
public record SubprocessorNoticeRequest(
    @NotBlank @Size(max = 200) String name,
    @NotBlank @Size(max = 500) String purpose,
    @NotBlank @Size(max = 500) String dataCategories,
    @NotBlank @Size(max = 200) String location,
    @NotNull LocalDate effectiveFrom
) {}
