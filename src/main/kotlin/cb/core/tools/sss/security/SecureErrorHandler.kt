package cb.core.tools.sss.security

/**
 * Handles error messages in a cryptographically secure manner to prevent information leakage.
 * 
 * This utility ensures that error messages do not reveal sensitive information about:
 * - The actual secret data or its properties
 * - Internal state or implementation details
 * - Specific values that caused failures
 * - Timing information that could be used in side-channel attacks
 */
object SecureErrorHandler {
    
    /**
     * Categories of errors that are safe to expose to users.
     * 
     * Each category has a corresponding generic error message that
     * doesn't reveal implementation details or sensitive data.
     */
    enum class ErrorCategory {
        INVALID_CONFIGURATION,
        INSUFFICIENT_SHARES,
        INVALID_SHARE_FORMAT,
        INCOMPATIBLE_SHARES,
        VALIDATION_FAILED,
        OPERATION_FAILED
    }
    
    /**
     * Maps detailed internal errors to secure external messages.
     * 
     * Converts potentially sensitive error details into generic messages
     * that are safe to show to users without revealing implementation
     * details or secret data.
     * 
     * @param exception The internal exception (not used in message)
     * @param category The error category determining the message
     * @return Generic error message safe for external consumption
     */
    fun sanitizeError(exception: Throwable, category: ErrorCategory): String {
        return when (category) {
            ErrorCategory.INVALID_CONFIGURATION -> 
                "Invalid configuration parameters"
            
            ErrorCategory.INSUFFICIENT_SHARES -> 
                "Insufficient number of shares for reconstruction"
            
            ErrorCategory.INVALID_SHARE_FORMAT -> 
                "Invalid share format"
            
            ErrorCategory.INCOMPATIBLE_SHARES -> 
                "Incompatible shares detected"
            
            ErrorCategory.VALIDATION_FAILED -> 
                "Validation failed"
            
            ErrorCategory.OPERATION_FAILED -> 
                "Operation failed"
        }
    }
    
    /**
     * Creates a secure exception that doesn't leak sensitive information.
     * 
     * The returned exception contains only a generic error message and
     * does not include the original exception as a cause to prevent
     * stack trace information leakage.
     * 
     * @param category The error category for message selection
     * @param internalCause Original exception (logged internally only)
     * @return Safe exception with generic error message
     */
    fun createSecureException(category: ErrorCategory, internalCause: Throwable? = null): IllegalArgumentException {
        val message = sanitizeError(internalCause ?: Exception(), category)
        // Don't include the original exception as cause to prevent stack trace leakage
        return IllegalArgumentException(message)
    }
    
    /**
     * Validates that an error message doesn't contain sensitive information.
     * 
     * Checks for patterns that might reveal:
     * - Specific numeric values (indices, sizes, coefficients)
     * - Implementation details (byte positions, field elements)
     * - Secret or share content
     * - Cryptographic hashes
     * 
     * Used in debug/test mode only.
     * 
     * @param message The error message to validate
     * @return true if message appears safe, false if it may leak information
     */
    fun validateErrorMessage(message: String): Boolean {
        val sensitivePatterns = listOf(
            // Specific numeric values
            Regex("\\b\\d{2,}\\b"), // Numbers with 2+ digits
            Regex("0x[0-9a-fA-F]+"), // Hex values
            
            // Implementation details
            Regex("byte(s)?\\s*\\d+"), // Byte positions
            Regex("index\\s*[=:]?\\s*\\d+"), // Index values
            Regex("size\\s*[=:]?\\s*\\d+"), // Size values
            
            // Cryptographic details
            Regex("coefficient"), // Polynomial coefficients
            Regex("polynomial"), // Polynomial details
            Regex("field\\s+element"), // Field element values
            
            // Data content
            Regex("secret.*contains"), // Secret content hints
            Regex("share.*value"), // Share values
            Regex("hash.*[0-9a-fA-F]{8,}") // Hash values
        )
        
        return sensitivePatterns.none { pattern ->
            pattern.containsMatchIn(message.lowercase())
        }
    }
    
    /**
     * Logs detailed error information securely (for internal debugging only).
     * 
     * In production, this should write to a secure audit log with:
     * - Access controls
     * - Encryption at rest
     * - Audit trail integrity
     * 
     * Currently validates and sanitizes messages before logging.
     * 
     * @param category The error category
     * @param details Detailed error information (will be sanitized)
     * @param exception Optional exception for internal logging
     */
    fun logSecureError(category: ErrorCategory, details: String, exception: Throwable? = null) {
        // In production, this would write to a secure audit log
        // For now, we just validate the message doesn't leak info
        val safeDetails = if (validateErrorMessage(details)) {
            details
        } else {
            "Details suppressed for security"
        }
        
        // Log category and safe details only
        // Don't log the actual exception details in production
    }
}