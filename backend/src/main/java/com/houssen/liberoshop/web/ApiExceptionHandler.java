package com.houssen.liberoshop.web;

import com.houssen.liberoshop.service.exception.BusinessRuleException;
import com.houssen.liberoshop.service.exception.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.List;

/**
 * Business and validation failures as JSON.
 *
 * <p>License failures are deliberately not handled here: {@code LicenseExceptionHandler}
 * already maps them to 402/403 and must keep doing so, since the front end keys its
 * renewal banner on that status.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(ResourceNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiError("NOT_FOUND", e.getMessage(), List.of(), Instant.now()));
    }

    /**
     * 409 rather than 400: the payload was understood, the shop's rules refuse it. The UI
     * shows the message as is, so these are written for a cashier, not for a developer.
     */
    @ExceptionHandler(BusinessRuleException.class)
    public ResponseEntity<ApiError> handleBusinessRule(BusinessRuleException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiError(e.code(), e.getMessage(), List.of(), Instant.now()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException e) {
        List<String> details = e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + " : " + error.getDefaultMessage())
                .toList();
        return ResponseEntity.badRequest()
                .body(new ApiError("VALIDATION_FAILED", "Les donnees envoyees sont incompletes.",
                        details, Instant.now()));
    }

    /**
     * The caller presented a valid token, but the account behind it can no longer act: a
     * super-admin disabled it, or it was removed from under an open session.
     * {@code CurrentUser} says which, and that message is passed through as is.
     *
     * <p>Kept apart from the handler below, which is vague on purpose to stop someone
     * probing which login names exist. There is nothing to hide here: the holder of the
     * token already knows whose account it is, and being told "identifiant ou mot de passe
     * incorrect" after a clean sign-in would send them looking for a fault of their own.
     */
    @ExceptionHandler(AuthenticationCredentialsNotFoundException.class)
    public ResponseEntity<ApiError> handleUnusableAccount(AuthenticationCredentialsNotFoundException e) {
        log.info("Requete authentifiee sur un compte inutilisable : {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ApiError("ACCOUNT_UNUSABLE", e.getMessage(), List.of(), Instant.now()));
    }

    /** Wrong password or unknown account -- answered identically on purpose. */
    @ExceptionHandler({BadCredentialsException.class, AuthenticationException.class})
    public ResponseEntity<ApiError> handleBadCredentials(AuthenticationException e) {
        log.info("Echec d'authentification : {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ApiError("BAD_CREDENTIALS", "Identifiant ou mot de passe incorrect.",
                        List.of(), Instant.now()));
    }

    /**
     * @param code    stable identifier the client can branch on
     * @param message end-user text, already in French and safe to display as is
     * @param details field-level messages, empty unless this is a validation failure
     */
    public record ApiError(String code, String message, List<String> details, Instant timestamp) {
    }
}
