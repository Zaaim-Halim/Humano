package com.humano.web.rest.errors;

import java.io.Serializable;

public record FieldErrorVM(String objectName, String field, String message) implements Serializable {

    private static final long serialVersionUID = 1L;

}
