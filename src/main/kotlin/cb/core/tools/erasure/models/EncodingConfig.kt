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

data class EncodingConfig(
    val dataShards: Int,
    val parityShards: Int,
    val shardSize: Int = 8192,
    val decodingOptions: DecodingOptions = DecodingOptions()
) {
    val totalShards: Int = dataShards + parityShards
    
    init {
        require(dataShards > 0) { "Data shards must be positive" }
        require(parityShards > 0) { "Parity shards must be positive" }
        require(totalShards <= 256) { "Total shards cannot exceed 256" }
        require(shardSize > 0) { "Shard size must be positive" }
    }
}

data class DecodingOptions(
    val strictValidation: Boolean = false,
    val preferredStrategy: ReconstructionStrategy = ReconstructionStrategy.AUTO,
    val enableCaching: Boolean = true,
    val matrixCacheSize: Int = 100,
    val errorToleranceThreshold: Double = 0.0,
    val enableDiagnostics: Boolean = false,
    val timeoutMs: Long? = null,
    val parallelChunks: Int = 1,
    val debugMode: Boolean = false
)

enum class ReconstructionStrategy {
    AUTO,
    FAST_PATH_ONLY,
    SYNDROME_PREFERRED,
    MATRIX_INVERSION_PREFERRED,
    POLYNOMIAL_INTERPOLATION_PREFERRED,
    HYBRID_ADAPTIVE
}

data class PrecomputedMatrices(
    val encodingMatrix: Array<ByteArray>,
    val inverseCandidates: Map<Set<Int>, Array<ByteArray>>
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as PrecomputedMatrices

        if (!encodingMatrix.contentDeepEquals(other.encodingMatrix)) return false
        if (inverseCandidates != other.inverseCandidates) return false

        return true
    }

    override fun hashCode(): Int {
        var result = encodingMatrix.contentDeepHashCode()
        result = 31 * result + inverseCandidates.hashCode()
        return result
    }
}