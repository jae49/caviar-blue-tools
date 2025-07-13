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

data class Shard(
    val index: Int,
    val data: ByteArray,
    val metadata: ShardMetadata
) {
    val isDataShard: Boolean = index < metadata.config.dataShards
    val isParityShard: Boolean = !isDataShard

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Shard

        if (index != other.index) return false
        if (!data.contentEquals(other.data)) return false
        if (metadata != other.metadata) return false

        return true
    }

    override fun hashCode(): Int {
        var result = index
        result = 31 * result + data.contentHashCode()
        result = 31 * result + metadata.hashCode()
        return result
    }
}

data class ShardMetadata(
    val originalSize: Long,
    val config: EncodingConfig,
    val checksum: String,
    val timestamp: Long = System.currentTimeMillis(),
    val chunkIndex: Int? = null
)