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
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Assertions.*
import kotlin.random.Random
import kotlin.system.measureNanoTime

@Tag("slow")
class TwentyShardPerformanceTest {

    private val configurations = listOf(
        EncodingConfig(dataShards = 10, parityShards = 10),
        EncodingConfig(dataShards = 12, parityShards = 8),
        EncodingConfig(dataShards = 14, parityShards = 6),
        EncodingConfig(dataShards = 16, parityShards = 4),
    )

    @Test
    fun testTwentyShardPerformance() {
        println("\n=== 20 Shard Performance Test ===")
        println("-".repeat(80))

        val dataSizes = listOf(100 * 1024, 500 * 1024, 1024 * 1024, 2 * 1024 * 1024)
        testEncoderPerformance(ReedSolomonEncoder(), ReedSolomonDecoder(), dataSizes, configurations)
    }

    @Test
    fun test1MBWith20ShardsDetailed() {
        println("\n=== Detailed 1MB with 20 Shards Test ===")
        println("-".repeat(80))

        val dataSize = 1024 * 1024 // 1 MB
        val config = EncodingConfig(dataShards = 12, parityShards = 8) // 12+8 = 20 shards
        val data = ByteArray(dataSize) { Random.nextBytes(1)[0] }

        val encoder = ReedSolomonEncoder()
        val decoder = ReedSolomonDecoder()

        encoder.encode(data, config) // warm up
        val avgEncodingTime = (1..5).map { measureNanoTime { encoder.encode(data, config) } }
            .average() / 1_000_000.0
        val mb = dataSize / (1024.0 * 1024.0)
        println("Encoding: ${"%.2f".format(avgEncodingTime)}ms (${"%.2f".format(mb / (avgEncodingTime / 1000.0))} MB/s)")

        // Decode under increasing erasure counts; every case must recover exactly.
        // Erase the first `erasureCount` data shards in every chunk (by local index),
        // which always forces the matrix-inversion path and keeps k shards per chunk.
        val shards = encoder.encode(data, config)
        println("Decoding performance by erasure count:")
        for (erasureCount in listOf(1, 2, 4, 6, 8)) {
            val erased = (0 until erasureCount).toSet()
            val available = shards.filter { (it.index % config.totalShards) !in erased }
            val times = (1..3).map {
                var result: ReconstructionResult? = null
                val t = measureNanoTime { result = decoder.decode(available) }
                assertTrue(result is ReconstructionResult.Success) { "Decode failed with $erasureCount erasures" }
                assertArrayEquals(data, (result as ReconstructionResult.Success).data)
                t
            }
            val avg = times.average() / 1_000_000.0
            println("  $erasureCount erasures: ${"%.2f".format(avg)}ms (${"%.2f".format(mb / (avg / 1000.0))} MB/s)")
        }
    }

    private fun testEncoderPerformance(
        encoder: ReedSolomonEncoder,
        decoder: ReedSolomonDecoder,
        dataSizes: List<Int>,
        configurations: List<EncodingConfig>,
    ) {
        for (config in configurations) {
            println("\nConfiguration: ${config.dataShards}+${config.parityShards} (${config.totalShards} total)")
            println("-".repeat(60))

            for (dataSize in dataSizes) {
                val data = ByteArray(dataSize) { Random.nextBytes(1)[0] }

                encoder.encode(data, config) // warm up
                val avgEncodingTime = (1..3).map { measureNanoTime { encoder.encode(data, config) } }
                    .average() / 1_000_000.0

                val dataSizeMB = dataSize / (1024.0 * 1024.0)
                val encodingThroughput = dataSizeMB / (avgEncodingTime / 1000.0)

                // Erase the maximum recoverable shards per chunk (the first
                // parityShards data shards by local index) to exercise reconstruction.
                val shards = encoder.encode(data, config)
                val erased = (0 until config.parityShards).toSet()
                val available = shards.filter { (it.index % config.totalShards) !in erased }

                val decodingTimes = (1..3).map {
                    var result: ReconstructionResult? = null
                    val t = measureNanoTime { result = decoder.decode(available) }
                    assertTrue(result is ReconstructionResult.Success) {
                        "Decode failed for ${config.dataShards}+${config.parityShards}"
                    }
                    assertArrayEquals(data, (result as ReconstructionResult.Success).data)
                    t
                }
                val avgDecodingTime = decodingTimes.average() / 1_000_000.0
                val decodingThroughput = dataSizeMB / (avgDecodingTime / 1000.0)

                val dataSizeStr = if (dataSize < 1024 * 1024) "${dataSize / 1024}KB" else "${dataSize / (1024 * 1024)}MB"
                println(
                    String.format(
                        "%-6s | Enc: %6.2fms (%6.2f MB/s) | Dec: %6.2fms (%6.2f MB/s)",
                        dataSizeStr, avgEncodingTime, encodingThroughput, avgDecodingTime, decodingThroughput,
                    ),
                )
            }
        }
    }
}
