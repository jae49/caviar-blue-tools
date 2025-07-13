/*
 * Copyright 2025 John Engelman
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cb.core.tools.sss.models

/**
 * Sealed class hierarchy representing results of SSS operations.
 */
sealed class SSSResult<out T> {
    /**
     * Successful operation result.
     */
    data class Success<T>(val value: T) : SSSResult<T>()

    /**
     * Failed operation result with error details.
     */
    data class Failure(
        val error: SSSError,
        val message: String,
        val cause: Throwable? = null
    ) : SSSResult<Nothing>()

    /**
     * Partial reconstruction result when some shares are invalid but reconstruction may still be possible.
     */
    data class PartialReconstruction(
        val validShares: List<SecretShare>,
        val invalidShares: List<InvalidShare>,
        val canReconstruct: Boolean,
        val missingShareCount: Int
    ) : SSSResult<Nothing>()

    /**
     * Checks if the result is successful.
     * 
     * @return true if this is a Success result, false otherwise
     */
    fun isSuccess(): Boolean = this is Success

    /**
     * Checks if the result is a failure.
     * 
     * @return true if this is a Failure result, false otherwise
     */
    fun isFailure(): Boolean = this is Failure

    /**
     * Gets the success value or null.
     * 
     * Safe way to extract the value without throwing exceptions.
     * 
     * @return The success value, or null if this is not a Success result
     */
    fun getOrNull(): T? = when (this) {
        is Success -> value
        else -> null
    }

    /**
     * Gets the success value or throws the exception.
     * 
     * @return The success value
     * @throws SSSException if this is not a Success result
     */
    fun getOrThrow(): T = when (this) {
        is Success -> value
        is Failure -> throw SSSException(message, cause)
        is PartialReconstruction -> throw SSSException(
            "Partial reconstruction: ${validShares.size} valid shares, " +
            "${invalidShares.size} invalid shares, $missingShareCount shares needed"
        )
    }

    /**
     * Maps the success value to another type.
     * 
     * Applies the transformation only if this is a Success result.
     * Failure and PartialReconstruction results are propagated unchanged.
     * 
     * @param transform Function to transform the success value
     * @return New result with transformed value or original failure
     */
    inline fun <R> map(transform: (T) -> R): SSSResult<R> = when (this) {
        is Success -> Success(transform(value))
        is Failure -> this
        is PartialReconstruction -> Failure(
            SSSError.PARTIAL_DATA,
            "Cannot map partial reconstruction result"
        )
    }

    /**
     * Flat maps the success value to another result.
     * 
     * Allows chaining operations that return SSSResult.
     * If this is a Success, applies the transformation.
     * Otherwise, propagates the failure.
     * 
     * @param transform Function that returns a new SSSResult
     * @return Result of the transformation or original failure
     */
    inline fun <R> flatMap(transform: (T) -> SSSResult<R>): SSSResult<R> = when (this) {
        is Success -> transform(value)
        is Failure -> this
        is PartialReconstruction -> Failure(
            SSSError.PARTIAL_DATA,
            "Cannot flat map partial reconstruction result"
        )
    }

    companion object {
        /**
         * Creates a success result.
         * 
         * @param value The successful value
         * @return Success result wrapping the value
         */
        fun <T> success(value: T): SSSResult<T> = Success(value)

        /**
         * Creates a failure result.
         * 
         * @param error The error type
         * @param message Human-readable error message
         * @param cause Optional underlying exception
         * @return Failure result with error details
         */
        fun failure(error: SSSError, message: String, cause: Throwable? = null): SSSResult<Nothing> =
            Failure(error, message, cause)

        /**
         * Creates a partial reconstruction result.
         * 
         * Used when some shares are invalid but reconstruction might still be possible
         * if enough valid shares remain.
         * 
         * @param validShares List of valid shares
         * @param invalidShares List of invalid shares with reasons
         * @param threshold Minimum shares needed for reconstruction
         * @return PartialReconstruction result with analysis
         */
        fun partialReconstruction(
            validShares: List<SecretShare>,
            invalidShares: List<InvalidShare>,
            threshold: Int
        ): SSSResult<Nothing> {
            val canReconstruct = validShares.size >= threshold
            val missingShareCount = if (canReconstruct) 0 else threshold - validShares.size
            return PartialReconstruction(validShares, invalidShares, canReconstruct, missingShareCount)
        }

        /**
         * Converts a nullable value to a result.
         * 
         * @param value The nullable value
         * @param error Error type if value is null
         * @param message Error message if value is null
         * @return Success if value is non-null, Failure otherwise
         */
        fun <T> fromNullable(value: T?, error: SSSError, message: String): SSSResult<T> =
            value?.let { Success(it) } ?: Failure(error, message)

        /**
         * Wraps a computation that might throw in a result.
         * 
         * Catches exceptions and converts them to Failure results.
         * SSSException preserves the error type, other exceptions become UNKNOWN.
         * 
         * @param block The computation to execute
         * @return Success with result or Failure with exception details
         */
        inline fun <T> catching(block: () -> T): SSSResult<T> = try {
            Success(block())
        } catch (e: SSSException) {
            Failure(e.error, e.message ?: "SSS operation failed", e)
        } catch (e: Exception) {
            Failure(SSSError.UNKNOWN, e.message ?: "Unknown error", e)
        }
    }
}

/**
 * Represents an invalid share with reason.
 * 
 * Used in partial reconstruction results to track which shares
 * failed validation and why.
 * 
 * @property share The invalid share
 * @property reason Human-readable explanation of why the share is invalid
 */
data class InvalidShare(
    val share: SecretShare,
    val reason: String
)

/**
 * SSS-specific error types.
 * 
 * Categorizes different failure modes in SSS operations for
 * appropriate error handling and user feedback.
 */
enum class SSSError {
    INVALID_CONFIG,
    INVALID_SECRET,
    INVALID_SHARE,
    INSUFFICIENT_SHARES,
    INCOMPATIBLE_SHARES,
    RECONSTRUCTION_FAILED,
    VALIDATION_FAILED,
    SERIALIZATION_ERROR,
    PARTIAL_DATA,
    UNKNOWN
}

/**
 * SSS-specific exception.
 * 
 * Custom exception type that includes an SSSError category for
 * structured error handling.
 * 
 * @property message Error description
 * @property cause Optional underlying exception
 * @property error The categorized error type
 */
class SSSException(
    message: String,
    cause: Throwable? = null,
    val error: SSSError = SSSError.UNKNOWN
) : Exception(message, cause)