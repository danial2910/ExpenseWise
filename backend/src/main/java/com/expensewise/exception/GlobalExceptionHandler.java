package com.expensewise.exception;

import com.expensewise.common.ApiErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException ex,
                                                               HttpServletRequest request) {
        Map<String, String> fieldErrors = new HashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(fieldError.getField(), fieldError.getDefaultMessage());
        }

        ApiErrorResponse body = new ApiErrorResponse(
                Instant.now(),
                HttpStatus.BAD_REQUEST.value(),
                "VALIDATION_FAILED",
                "One or more fields are invalid",
                request.getRequestURI(),
                fieldErrors
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(ResourceNotFoundException ex,
                                                             HttpServletRequest request) {
        ApiErrorResponse body = new ApiErrorResponse(
                Instant.now(),
                HttpStatus.NOT_FOUND.value(),
                "NOT_FOUND",
                ex.getMessage(),
                request.getRequestURI(),
                null
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidCredentials(InvalidCredentialsException ex,
                                                                       HttpServletRequest request) {
        return unauthorized("INVALID_CREDENTIALS", ex.getMessage(), request);
    }

    @ExceptionHandler(AccountDisabledException.class)
    public ResponseEntity<ApiErrorResponse> handleAccountDisabled(AccountDisabledException ex,
                                                                     HttpServletRequest request) {
        return unauthorized("ACCOUNT_DISABLED", ex.getMessage(), request);
    }

    @ExceptionHandler(InvalidTokenException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidToken(InvalidTokenException ex,
                                                                 HttpServletRequest request) {
        ApiErrorResponse body = new ApiErrorResponse(
                Instant.now(),
                HttpStatus.BAD_REQUEST.value(),
                "INVALID_TOKEN",
                ex.getMessage(),
                request.getRequestURI(),
                null
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(RateLimitedException.class)
    public ResponseEntity<ApiErrorResponse> handleRateLimited(RateLimitedException ex,
                                                                 HttpServletRequest request) {
        ApiErrorResponse body = new ApiErrorResponse(
                Instant.now(),
                HttpStatus.TOO_MANY_REQUESTS.value(),
                "RATE_LIMITED",
                ex.getMessage(),
                request.getRequestURI(),
                null
        );
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(body);
    }

    @ExceptionHandler(EmailAlreadyExistsException.class)
    public ResponseEntity<ApiErrorResponse> handleEmailAlreadyExists(EmailAlreadyExistsException ex,
                                                                        HttpServletRequest request) {
        return validationFailed("email", ex.getMessage(), request);
    }

    @ExceptionHandler(WeakPasswordException.class)
    public ResponseEntity<ApiErrorResponse> handleWeakPassword(WeakPasswordException ex,
                                                                   HttpServletRequest request) {
        return validationFailed("password", ex.getMessage(), request);
    }

    @ExceptionHandler(DuplicateCategoryException.class)
    public ResponseEntity<ApiErrorResponse> handleDuplicateCategory(DuplicateCategoryException ex,
                                                                        HttpServletRequest request) {
        return validationFailed("name", ex.getMessage(), request);
    }

    @ExceptionHandler(CategoryInUseException.class)
    public ResponseEntity<ApiErrorResponse> handleCategoryInUse(CategoryInUseException ex,
                                                                    HttpServletRequest request) {
        ApiErrorResponse body = new ApiErrorResponse(
                Instant.now(),
                HttpStatus.CONFLICT.value(),
                "CATEGORY_IN_USE",
                ex.getMessage(),
                request.getRequestURI(),
                null
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @ExceptionHandler(InvalidTransactionCategoryException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidTransactionCategory(InvalidTransactionCategoryException ex,
                                                                                 HttpServletRequest request) {
        return validationFailed("categoryId", ex.getMessage(), request);
    }

    @ExceptionHandler(InvalidBudgetCategoryException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidBudgetCategory(InvalidBudgetCategoryException ex,
                                                                          HttpServletRequest request) {
        return validationFailed("categoryId", ex.getMessage(), request);
    }

    @ExceptionHandler(InvalidBudgetPeriodException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidBudgetPeriod(InvalidBudgetPeriodException ex,
                                                                        HttpServletRequest request) {
        return validationFailed("periodMonth", ex.getMessage(), request);
    }

    @ExceptionHandler(DuplicateBudgetException.class)
    public ResponseEntity<ApiErrorResponse> handleDuplicateBudget(DuplicateBudgetException ex,
                                                                    HttpServletRequest request) {
        return validationFailed("periodMonth", ex.getMessage(), request);
    }

    @ExceptionHandler(OverallBudgetRequiredException.class)
    public ResponseEntity<ApiErrorResponse> handleOverallBudgetRequired(OverallBudgetRequiredException ex,
                                                                          HttpServletRequest request) {
        return validationFailed("categoryId", ex.getMessage(), request);
    }

    @ExceptionHandler(BudgetExceedsOverallException.class)
    public ResponseEntity<ApiErrorResponse> handleBudgetExceedsOverall(BudgetExceedsOverallException ex,
                                                                         HttpServletRequest request) {
        return validationFailed("amount", ex.getMessage(), request);
    }

    @ExceptionHandler(OverallBudgetInUseException.class)
    public ResponseEntity<ApiErrorResponse> handleOverallBudgetInUse(OverallBudgetInUseException ex,
                                                                       HttpServletRequest request) {
        ApiErrorResponse body = new ApiErrorResponse(
                Instant.now(),
                HttpStatus.CONFLICT.value(),
                "OVERALL_BUDGET_IN_USE",
                ex.getMessage(),
                request.getRequestURI(),
                null
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @ExceptionHandler(AiChatUnavailableException.class)
    public ResponseEntity<ApiErrorResponse> handleAiChatUnavailable(AiChatUnavailableException ex,
                                                                      HttpServletRequest request) {
        ApiErrorResponse body = new ApiErrorResponse(
                Instant.now(),
                HttpStatus.SERVICE_UNAVAILABLE.value(),
                "AI_UNAVAILABLE",
                "The AI assistant is temporarily unavailable. Please try again shortly.",
                request.getRequestURI(),
                null
        );
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(body);
    }

    @ExceptionHandler(AuthorizationDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleAuthorizationDenied(AuthorizationDeniedException ex,
                                                                          HttpServletRequest request) {
        // @PreAuthorize throws this inside the controller call, so it reaches this
        // @RestControllerAdvice before the security filter chain's 403 handler would.
        ApiErrorResponse body = new ApiErrorResponse(
                Instant.now(),
                HttpStatus.FORBIDDEN.value(),
                "FORBIDDEN",
                "You do not have permission to access this resource",
                request.getRequestURI(),
                null
        );
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
    }

    @ExceptionHandler(SelfActionNotAllowedException.class)
    public ResponseEntity<ApiErrorResponse> handleSelfActionNotAllowed(SelfActionNotAllowedException ex,
                                                                          HttpServletRequest request) {
        ApiErrorResponse body = new ApiErrorResponse(
                Instant.now(),
                HttpStatus.FORBIDDEN.value(),
                "FORBIDDEN",
                ex.getMessage(),
                request.getRequestURI(),
                null
        );
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
    }

    private ResponseEntity<ApiErrorResponse> unauthorized(String error, String message, HttpServletRequest request) {
        ApiErrorResponse body = new ApiErrorResponse(
                Instant.now(),
                HttpStatus.UNAUTHORIZED.value(),
                error,
                message,
                request.getRequestURI(),
                null
        );
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body);
    }

    private ResponseEntity<ApiErrorResponse> validationFailed(String field, String message, HttpServletRequest request) {
        ApiErrorResponse body = new ApiErrorResponse(
                Instant.now(),
                HttpStatus.BAD_REQUEST.value(),
                "VALIDATION_FAILED",
                message,
                request.getRequestURI(),
                Map.of(field, message)
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception on {}", request.getRequestURI(), ex);

        ApiErrorResponse body = new ApiErrorResponse(
                Instant.now(),
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "INTERNAL_ERROR",
                "An unexpected error occurred",
                request.getRequestURI(),
                null
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
}
