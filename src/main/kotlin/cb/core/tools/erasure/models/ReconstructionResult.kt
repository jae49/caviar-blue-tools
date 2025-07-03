package cb.core.tools.erasure.models

sealed class ReconstructionResult {
    data class Success(val data: ByteArray) : ReconstructionResult() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Success

            return data.contentEquals(other.data)
        }

        override fun hashCode(): Int {
            return data.contentHashCode()
        }
    }
    
    data class Failure(
        val error: ReconstructionError,
        val message: String? = null
    ) : ReconstructionResult()
    data class Partial(val recoveredBytes: Long, val totalBytes: Long) : ReconstructionResult()
}

enum class ReconstructionError {
    INSUFFICIENT_SHARDS,
    CORRUPTED_SHARDS,
    INVALID_CONFIGURATION,
    MATH_ERROR
}