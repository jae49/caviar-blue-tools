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

package cb.core.tools.erasure.performance

import cb.core.tools.erasure.ReedSolomonEncoder
import cb.core.tools.erasure.ReedSolomonDecoder
import cb.core.tools.erasure.models.EncodingConfig
import cb.core.tools.erasure.models.ReconstructionResult
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import kotlin.random.Random
import kotlin.system.measureNanoTime

/**
 * Throughput sanity check for the canonical Reed-Solomon encoder/decoder.
 *
 * Unlike the old version, this test ASSERTS that every configuration round-trips
 * correctly after the maximum number of erasures (including data shards) — a
 * decode failure here is a hard test failure, not a printed warning.
 */
class SimplePerformanceTest {

    private val configurations = listOf(
        EncodingConfig(dataShards = 4, parityShards = 2),
        EncodingConfig(dataShards = 8, parityShards = 4),
        EncodingConfig(dataShards = 10, parityShards = 10),
        EncodingConfig(dataShards = 12, parityShards = 8),
        EncodingConfig(dataShards = 14, parityShards = 6),
        EncodingConfig(dataShards = 16, parityShards = 4),
    )

    @Test
    fun `encoder round-trips and reports throughput across configurations`() {
        val dataSize = 256 * 1024 // 256 KB
        val encoder = ReedSolomonEncoder()
        val decoder = ReedSolomonDecoder()

        println("\n=== Reed-Solomon throughput (256 KB) ===")
        println("Config        | Encode (MB/s) | Decode (MB/s) | Recovered")
        println("-".repeat(64))

        val mb = dataSize / (1024.0 * 1024.0)
        for (config in configurations) {
            val data = ByteArray(dataSize) { Random.nextBytes(1)[0] }

            // Warm up + measure encode.
            encoder.encode(data, config)
            val encodeMs = (1..3).map { measureNanoTime { encoder.encode(data, config) } }
                .average() / 1_000_000.0
            val shards = encoder.encode(data, config)

            // Erase the maximum recoverable number of shards (parityShards) in every
            // chunk, taking them from the data shards so the matrix-inversion path is
            // exercised rather than the all-data fast path. Erasing by local index
            // keeps exactly k shards per chunk regardless of how many chunks there are.
            val erased = (0 until config.parityShards).toSet()
            val available = shards.filter { (it.index % config.totalShards) !in erased }

            var result: ReconstructionResult? = null
            val decodeMs = measureNanoTime { result = decoder.decode(available) } / 1_000_000.0

            assertTrue(result is ReconstructionResult.Success) {
                "Decode failed for ${config.dataShards}+${config.parityShards} after erasing ${config.parityShards} shards/chunk"
            }
            assertArrayEquals(data, (result as ReconstructionResult.Success).data)

            println(
                String.format(
                    "%-12s | %13.1f | %13.1f | %d erased/chunk ✓",
                    "${config.dataShards}+${config.parityShards}",
                    mb / (encodeMs / 1000.0),
                    mb / (decodeMs / 1000.0),
                    config.parityShards,
                ),
            )
        }
    }
}
