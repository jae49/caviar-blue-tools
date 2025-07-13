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