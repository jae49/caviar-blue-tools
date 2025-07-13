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
    val shardSize: Int = 8192
) {
    val totalShards: Int = dataShards + parityShards
    
    init {
        require(dataShards > 0) { "Data shards must be positive" }
        require(parityShards > 0) { "Parity shards must be positive" }
        require(totalShards <= 256) { "Total shards cannot exceed 256" }
        require(shardSize > 0) { "Shard size must be positive" }
    }
}