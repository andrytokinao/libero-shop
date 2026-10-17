package com.houssen.liberoshop.web;

import com.houssen.liberoshop.web.ApiExceptionHandler.ApiError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.List;

/**
 * Whatever no other handler answered, in the application's own format rather than Spring's: the
 * screens read {@code code} and {@code message} on every error, and a failure nobody foresaw
 * must not show a stack of field names nor a blank toast.
 *
 * <p>Last in line ({@link Ordered#LOWEST_PRECEDENCE}): {@link ApiExceptionHandler} and the licence
 * handler answer first for what they know. A refused access goes back to Spring Security, which
 * answers 403; the framework's own refusals (unknown address, wrong method, unreadable body) keep
 * their status.
 */
@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
public class FallbackExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(FallbackExceptionHandler.class);

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception e) throws Exception {
        if (e instanceof AccessDeniedException) {
            throw e;
        }
        if (e instanceof ErrorResponse framework) {
            return refused(framework.getStatusCode());
        }
        if (e instanceof HttpMessageNotReadableException || e instanceof TypeMismatchException) {
            return refused(HttpStatus.BAD_REQUEST);
        }
        log.error("Erreur imprevue.", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiError("UNEXPECTED_ERROR",
                        "Une erreur imprevue est survenue. Reessayez ; si elle revient, "
                                + "le detail est dans le journal du serveur.",
                        List.of(), Instant.now()));
    }

    /** The request itself was refused by the framework: its status, said in words. */
    private static ResponseEntity<ApiError> refused(HttpStatusCode status) {
        String message = switch (status.value()) {
            case 404 -> "Adresse inconnue du serveur.";
            case 405 -> "Cette action n'est pas permise a cette adresse.";
            case 413 -> "Fichier trop volumineux.";
            case 415 -> "Format de donnees non pris en charge.";
            default -> status.is4xxClientError()
                    ? "La demande envoyee au serveur n'est pas valide."
                    : "Le serveur n'a pas pu traiter la demande.";
        };
        return ResponseEntity.status(status)
                .body(new ApiError("REQUEST_REFUSED", message, List.of(), Instant.now()));
    }
}
