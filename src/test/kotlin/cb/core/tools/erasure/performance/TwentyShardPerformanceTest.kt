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
import cb.core.tools.erasure.performance.OptimizedReedSolomonEncoder
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Assertions.*
import kotlin.random.Random
import kotlin.system.measureNanoTime

@Tag("slow")
class TwentyShardPerformanceTest {
    
    @Test
    fun testTwentyShardPerformance() {
        println("\n=== 20 Shard Performance Test ===")
        println("Target: 1 MB/s throughput")
        println("-".repeat(80))
        
        val dataSizes = listOf(
            100 * 1024,     // 100 KB
            500 * 1024,     // 500 KB
            1024 * 1024,    // 1 MB
            2 * 1024 * 1024 // 2 MB
        )
        
        // Test various 20-shard configurations
        val configurations = listOf(
            EncodingConfig(dataShards = 10, parityShards = 10),   // 10+10
            EncodingConfig(dataShards = 12, parityShards = 8),    // 12+8
            EncodingConfig(dataShards = 14, parityShards = 6),    // 14+6
            EncodingConfig(dataShards = 16, parityShards = 4)     // 16+4
        )
        
        println("\nStandard Encoder Performance:")
        testEncoderPerformance(ReedSolomonEncoder(), ReedSolomonDecoder(), dataSizes, configurations)
        
        println("\nOptimized Encoder Performance:")
        val optimizedEncoder = OptimizedReedSolomonEncoder()
        try {
            testEncoderPerformance(optimizedEncoder, ReedSolomonDecoder(), dataSizes, configurations)
        } finally {
            optimizedEncoder.shutdown()
        }
    }
    
    @Test
    fun test1MBWith20ShardsDetailed() {
        println("\n=== Detailed 1MB with 20 Shards Test ===")
        println("-".repeat(80))
        
        val dataSize = 1024 * 1024 // 1 MB
        val config = EncodingConfig(dataShards = 12, parityShards = 8) // 12+8 = 20 shards
        val data = ByteArray(dataSize) { Random.nextBytes(1)[0] }
        
        // Test standard encoder
        println("Standard Encoder:")
        val standardEncoder = ReedSolomonEncoder()
        val standardDecoder = ReedSolomonDecoder()
        
        // Warm up
        standardEncoder.encode(data, config)
        
        // Measure encoding
        val encodingTimes = mutableListOf<Long>()
        repeat(5) {
            val time = measureNanoTime {
                standardEncoder.encode(data, config)
            }
            encodingTimes.add(time)
        }
        
        val avgEncodingTime = encodingTimes.average() / 1_000_000.0 // ms
        val encodingThroughput = 1.0 / (avgEncodingTime / 1000.0) // MB/s
        
        println("  Encoding: ${String.format("%.2f", avgEncodingTime)}ms (${String.format("%.2f", encodingThroughput)} MB/s)")
        
        // Test decoding with various erasure patterns
        val shards = standardEncoder.encode(data, config)
        val erasureCounts = listOf(1, 2, 4, 6, 8)
        
        println("  Decoding performance by erasure count:")
        for (erasureCount in erasureCounts) {
            val decodingTimes = mutableListOf<Long>()
            var decodingSuccessful = true
            
            try {
                repeat(3) {
                    val availableShards = shards.shuffled(Random).drop(erasureCount)
                    val time = measureNanoTime {
                        val result = standardDecoder.decode(availableShards)
                        if (result !is ReconstructionResult.Success) {
                            decodingSuccessful = false
                            println("    Warning: Decoding failed with $erasureCount erasures")
                        }
                    }
                    if (decodingSuccessful) {
                        decodingTimes.add(time)
                    }
                }
                
                if (decodingSuccessful && decodingTimes.isNotEmpty()) {
                    val avgDecodingTime = decodingTimes.average() / 1_000_000.0 // ms
                    val decodingThroughput = 1.0 / (avgDecodingTime / 1000.0) // MB/s
                    
                    println("    $erasureCount erasures: ${String.format("%.2f", avgDecodingTime)}ms (${String.format("%.2f", decodingThroughput)} MB/s)")
                } else {
                    println("    $erasureCount erasures: FAILED")
                }
            } catch (e: Exception) {
                println("    $erasureCount erasures: ERROR - ${e.message}")
            }
        }
        
        // Test optimized encoder
        println("\nOptimized Encoder:")
        val optimizedEncoder = OptimizedReedSolomonEncoder()
        
        try {
            // Warm up
            optimizedEncoder.encode(data, config)
            
            val optimizedTimes = mutableListOf<Long>()
            repeat(5) {
                val time = measureNanoTime {
                    optimizedEncoder.encode(data, config)
                }
                optimizedTimes.add(time)
            }
            
            val avgOptimizedTime = optimizedTimes.average() / 1_000_000.0 // ms
            val optimizedThroughput = 1.0 / (avgOptimizedTime / 1000.0) // MB/s
            
            println("  Encoding: ${String.format("%.2f", avgOptimizedTime)}ms (${String.format("%.2f", optimizedThroughput)} MB/s)")
            println("  Speedup: ${String.format("%.2f", avgEncodingTime / avgOptimizedTime)}x")
            
            if (optimizedThroughput >= 1.0) {
                println("  ✓ Achieved target throughput of 1 MB/s!")
            } else {
                println("  ✗ Below target throughput (need ${String.format("%.2f", 1.0 / optimizedThroughput)}x improvement)")
            }
        } finally {
            optimizedEncoder.shutdown()
        }
    }
    
    @Test
    fun profileEncodingBottlenecks() {
        println("\n=== Profiling Encoding Bottlenecks ===")
        println("-".repeat(80))
        
        val config = EncodingConfig(dataShards = 12, parityShards = 8)
        val dataSize = 100 * 1024 // 100 KB for faster profiling
        val data = ByteArray(dataSize) { Random.nextBytes(1)[0] }
        
        val encoder = ReedSolomonEncoder()
        
        // Profile different stages
        val shardCreationTime = measureNanoTime {
            // Simulate data shard creation
            val chunks = data.size / config.dataShards
            for (i in 0 until config.dataShards) {
                val start = i * chunks
                val end = minOf(start + chunks, data.size)
                data.copyOfRange(start, end)
            }
        }
        
        println("Data shard creation: ${String.format("%.2f", shardCreationTime / 1_000_000.0)}ms")
        
        // Profile parity generation (this is usually the bottleneck)
        val totalTime = measureNanoTime {
            encoder.encode(data, config)
        }
        
        val parityTime = totalTime - shardCreationTime
        println("Parity generation: ${String.format("%.2f", parityTime / 1_000_000.0)}ms")
        println("Parity generation %: ${String.format("%.1f", (parityTime.toDouble() / totalTime) * 100)}%")
    }
    
    private fun testEncoderPerformance(
        encoder: Any,
        decoder: ReedSolomonDecoder,
        dataSizes: List<Int>,
        configurations: List<EncodingConfig>
    ) {
        for (config in configurations) {
            println("\nConfiguration: ${config.dataShards}+${config.parityShards} (${config.totalShards} total)")
            println("-".repeat(60))
            
            for (dataSize in dataSizes) {
                val data = ByteArray(dataSize) { Random.nextBytes(1)[0] }
                
                // Warm up
                when (encoder) {
                    is ReedSolomonEncoder -> encoder.encode(data, config)
                    is OptimizedReedSolomonEncoder -> encoder.encode(data, config)
                }
                
                // Measure encoding
                val encodingTimes = mutableListOf<Long>()
                repeat(3) {
                    val time = measureNanoTime {
                        when (encoder) {
                            is ReedSolomonEncoder -> encoder.encode(data, config)
                            is OptimizedReedSolomonEncoder -> encoder.encode(data, config)
                        }
                    }
                    encodingTimes.add(time)
                }
                
                val avgEncodingTime = encodingTimes.average() / 1_000_000.0 // ms
                val dataSizeMB = dataSize / (1024.0 * 1024.0)
                val encodingThroughput = dataSizeMB / (avgEncodingTime / 1000.0)
                
                // Measure decoding (with minimal erasures for consistency)
                val shards = when (encoder) {
                    is ReedSolomonEncoder -> encoder.encode(data, config)
                    is OptimizedReedSolomonEncoder -> encoder.encode(data, config)
                    else -> throw IllegalArgumentException("Unknown encoder type")
                }
                
                val availableShards = shards.shuffled(Random).drop(2) // Drop 2 shards
                
                val decodingTimes = mutableListOf<Long>()
                repeat(3) {
                    val time = measureNanoTime {
                        val result = decoder.decode(availableShards)
                        assertTrue(result is ReconstructionResult.Success)
                    }
                    decodingTimes.add(time)
                }
                
                val avgDecodingTime = decodingTimes.average() / 1_000_000.0 // ms
                val decodingThroughput = dataSizeMB / (avgDecodingTime / 1000.0)
                
                val dataSizeStr = when {
                    dataSize < 1024 * 1024 -> "${dataSize / 1024}KB"
                    else -> "${dataSize / (1024 * 1024)}MB"
                }
                
                println(String.format(
                    "%-6s | Enc: %6.2fms (%6.2f MB/s) | Dec: %6.2fms (%6.2f MB/s) %s",
                    dataSizeStr,
                    avgEncodingTime,
                    encodingThroughput,
                    avgDecodingTime,
                    decodingThroughput,
                    if (encodingThroughput >= 1.0) "✓" else ""
                ))
            }
        }
    }
}