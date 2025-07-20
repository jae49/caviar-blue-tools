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

package cb.core.tools.erasure.models

sealed class ReconstructionResult {
    data class Success(
        val data: ByteArray,
        val diagnostics: ReconstructionDiagnostics? = null
    ) : ReconstructionResult() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Success

            return data.contentEquals(other.data) && diagnostics == other.diagnostics
        }

        override fun hashCode(): Int {
            var result = data.contentHashCode()
            result = 31 * result + (diagnostics?.hashCode() ?: 0)
            return result
        }
    }
    
    data class Failure(
        val error: ReconstructionError,
        val message: String? = null,
        val diagnostics: ReconstructionDiagnostics? = null
    ) : ReconstructionResult()
    
    data class Partial(
        val recoveredBytes: Long, 
        val totalBytes: Long,
        val diagnostics: ReconstructionDiagnostics? = null
    ) : ReconstructionResult()
}

enum class ReconstructionError(val description: String) {
    INSUFFICIENT_SHARDS("Not enough shards available for reconstruction. Need at least k shards where k is the data shard count."),
    CORRUPTED_SHARDS("One or more shards contain corrupted data or have invalid checksums."),
    INVALID_CONFIGURATION("The encoding configuration is invalid or incompatible with the provided shards."),
    MATH_ERROR("Mathematical computation failed during Reed-Solomon decoding. This may indicate invalid shard combinations."),
    INCOMPATIBLE_SHARDS("The provided shards are from different encoding operations or have mismatched parameters."),
    MATRIX_INVERSION_FAILED("Failed to invert the decoding matrix. The shard combination may be mathematically degenerate."),
    POLYNOMIAL_INTERPOLATION_FAILED("Polynomial interpolation failed. The shard indices may not be unique or valid."),
    SHARD_SIZE_MISMATCH("Shards have different sizes. All shards must be the same size for reconstruction."),
    INVALID_SHARD_INDEX("One or more shard indices are out of bounds for the given configuration."),
    DECODING_TIMEOUT("Decoding operation timed out. Consider using a smaller chunk size or fewer shards.")
}

data class ReconstructionDiagnostics(
    val usedShardIndices: List<Int>,
    val attemptedCombinations: List<List<Int>>? = null,
    val reconstructionTimeMs: Long? = null,
    val memoryUsedBytes: Long? = null,
    val decodingStrategy: DecodingStrategy? = null,
    val warnings: List<String>? = null,
    val performanceMetrics: PerformanceMetrics? = null
)

enum class DecodingStrategy {
    FAST_PATH_ALL_DATA,
    SYNDROME_BASED,
    MATRIX_INVERSION,
    POLYNOMIAL_INTERPOLATION,
    HYBRID
}

data class PerformanceMetrics(
    val matrixOperationsCount: Int? = null,
    val fieldOperationsCount: Long? = null,
    val chunkProcessingTimeMs: Map<Int, Long>? = null,
    val cacheHitRate: Double? = null
)