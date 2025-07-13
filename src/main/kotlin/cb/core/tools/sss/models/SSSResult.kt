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
     */
    fun isSuccess(): Boolean = this is Success

    /**
     * Checks if the result is a failure.
     */
    fun isFailure(): Boolean = this is Failure

    /**
     * Gets the success value or null.
     */
    fun getOrNull(): T? = when (this) {
        is Success -> value
        else -> null
    }

    /**
     * Gets the success value or throws the exception.
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
         */
        fun <T> success(value: T): SSSResult<T> = Success(value)

        /**
         * Creates a failure result.
         */
        fun failure(error: SSSError, message: String, cause: Throwable? = null): SSSResult<Nothing> =
            Failure(error, message, cause)

        /**
         * Creates a partial reconstruction result.
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
         */
        fun <T> fromNullable(value: T?, error: SSSError, message: String): SSSResult<T> =
            value?.let { Success(it) } ?: Failure(error, message)

        /**
         * Wraps a computation that might throw in a result.
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
 */
data class InvalidShare(
    val share: SecretShare,
    val reason: String
)

/**
 * SSS-specific error types.
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
 */
class SSSException(
    message: String,
    cause: Throwable? = null,
    val error: SSSError = SSSError.UNKNOWN
) : Exception(message, cause)