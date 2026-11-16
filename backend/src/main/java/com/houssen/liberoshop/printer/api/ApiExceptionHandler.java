package com.houssen.liberoshop.printer.api;

import com.houssen.liberoshop.printer.exception.OrderNotFoundException;
import com.houssen.liberoshop.printer.exception.PrintException;
import com.houssen.liberoshop.printer.exception.PrinterConflictException;
import com.houssen.liberoshop.printer.exception.PrinterNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Limité aux contrôleurs du module pour ne pas interférer avec la gestion d'erreurs de l'application hôte. */
@RestControllerAdvice(basePackages = "com.houssen.liberoshop.printer.api")
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(PrinterNotFoundException.class)
    ProblemDetail notFound(PrinterNotFoundException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(OrderNotFoundException.class)
    ProblemDetail orderNotFound(OrderNotFoundException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(PrinterConflictException.class)
    ProblemDetail conflict(PrinterConflictException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail badRequest(IllegalArgumentException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(PrintException.class)
    ProblemDetail printFailed(PrintException e) {
        log.warn("Échec d'impression : {}", e.getMessage(), e);
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, e.getMessage());
    }
}
