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

package cb.core.tools.erasure

import cb.core.tools.erasure.matrix.SystematicRSDecoder
import cb.core.tools.erasure.models.*

class ReedSolomonDecoder {
    
    private val systematicDecoder = SystematicRSDecoder()
    
    fun decode(shards: List<Shard>): ReconstructionResult {
        return systematicDecoder.decode(shards)
    }
    
    fun canReconstruct(shards: List<Shard>, config: EncodingConfig): Boolean {
        return shards.size >= config.dataShards
    }
}