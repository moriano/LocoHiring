package com.locohiring.service.validators;

import com.locohiring.api.model.Resume;
import com.locohiring.service.ApiException;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/** Checks text and files sent by clients, beyond what the OpenAPI schema enforces. */
@Component
public class InputValidator {

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final int MAX_RESUME_BYTES = 10 * 1024 * 1024;

    public void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw ApiException.badRequest(field + " is required");
        }
    }

    public void validateEmail(String email) {
        if (email == null || !EMAIL.matcher(email.trim()).matches()) {
            throw ApiException.badRequest("'" + email + "' is not a valid email");
        }
    }

    /** The OpenAPI schema enforces the password length; this rejects blank names and malformed emails. */
    public void validateNewUser(String email, String firstName, String password) {
        validateEmail(email);
        requireText(firstName, "firstName");
        if (password == null || password.isBlank()) {
            throw ApiException.badRequest("password is required");
        }
    }

    public void validateUserUpdate(String email, String firstName) {
        validateEmail(email);
        requireText(firstName, "firstName");
    }

    public void validateResume(Resume resume) {
        requireText(resume.getFilename(), "filename");
        requireText(resume.getContentType(), "contentType");
        if (resume.getContent() == null || resume.getContent().length == 0) {
            throw ApiException.badRequest("The resume is empty");
        }
        if (resume.getContent().length > MAX_RESUME_BYTES) {
            throw ApiException.badRequest("The resume cannot be larger than 10 MB");
        }
    }
}
