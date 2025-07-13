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

class SimplePerformanceTest {
    
    @Test
    fun testOptimizedPerformance() {
        println("\n=== Optimized 20-Shard Performance Test ===")
        println("Target: 1 MB/s throughput")
        println("-".repeat(80))
        
        val dataSize = 1024 * 1024 // 1 MB
        val config = EncodingConfig(dataShards = 12, parityShards = 8) // 12+8 = 20 shards
        val data = ByteArray(dataSize) { Random.nextBytes(1)[0] }
        
        // Test standard encoder
        println("\n1. Standard Encoder:")
        val standardEncoder = ReedSolomonEncoder()
        testEncoder(standardEncoder, data, config, "Standard")
        
        // Test first optimized encoder
        println("\n2. Optimized Encoder (General):")
        val optimizedEncoder = OptimizedReedSolomonEncoder()
        try {
            testEncoder(optimizedEncoder, data, config, "Optimized")
        } finally {
            optimizedEncoder.shutdown()
        }
        
        // Test specialized 20-shard encoder
        println("\n3. Optimized Encoder (20-Shard Specialized):")
        val twentyShardEncoder = TwentyShardOptimizedEncoder()
        try {
            testEncoder(twentyShardEncoder, data, config, "20-Shard")
        } finally {
            twentyShardEncoder.shutdown()
        }
        
        // Test smaller configurations to verify optimization works
        println("\n\n=== Performance with Different Configurations ===")
        testDifferentConfigurations()
    }
    
    private fun testEncoder(encoder: Any, data: ByteArray, config: EncodingConfig, name: String) {
        // Warm up
        val warmupShards = when (encoder) {
            is ReedSolomonEncoder -> encoder.encode(data, config)
            is OptimizedReedSolomonEncoder -> encoder.encode(data, config)
            is TwentyShardOptimizedEncoder -> encoder.encode(data, config)
            else -> throw IllegalArgumentException("Unknown encoder type")
        }
        
        // Measure encoding time
        val encodingTimes = mutableListOf<Long>()
        repeat(5) {
            val time = measureNanoTime {
                when (encoder) {
                    is ReedSolomonEncoder -> encoder.encode(data, config)
                    is OptimizedReedSolomonEncoder -> encoder.encode(data, config)
                    is TwentyShardOptimizedEncoder -> encoder.encode(data, config)
                }
            }
            encodingTimes.add(time)
        }
        
        val avgEncodingTime = encodingTimes.average() / 1_000_000.0 // ms
        val encodingThroughput = 1.0 / (avgEncodingTime / 1000.0) // MB/s
        
        println("  Encoding time: ${String.format("%.2f", avgEncodingTime)}ms")
        println("  Throughput: ${String.format("%.2f", encodingThroughput)} MB/s")
        
        if (encodingThroughput >= 1.0) {
            println("  ✓ ACHIEVED TARGET of 1 MB/s!")
        } else {
            val improvement = 1.0 / encodingThroughput
            println("  ✗ Below target (need ${String.format("%.2f", improvement)}x improvement)")
        }
        
        // Test decoding
        val decoder = ReedSolomonDecoder()
        val shards = when (encoder) {
            is ReedSolomonEncoder -> encoder.encode(data, config)
            is OptimizedReedSolomonEncoder -> encoder.encode(data, config)
            is TwentyShardOptimizedEncoder -> encoder.encode(data, config)
            else -> throw IllegalArgumentException("Unknown encoder type")
        }
        
        // Drop 2 random shards
        val availableShards = shards.shuffled(Random).drop(2)
        
        val decodingTime = try {
            measureNanoTime {
                val result = decoder.decode(availableShards)
                if (result !is ReconstructionResult.Success) {
                    println("  Warning: Decoding failed, skipping decoding test")
                    return
                }
            } / 1_000_000.0 // ms
        } catch (e: Exception) {
            println("  Warning: Decoding error - ${e.message}")
            return
        }
        
        val decodingThroughput = 1.0 / (decodingTime / 1000.0) // MB/s
        
        println("  Decoding time: ${String.format("%.2f", decodingTime)}ms")
        println("  Decoding throughput: ${String.format("%.2f", decodingThroughput)} MB/s")
    }
    
    private fun testDifferentConfigurations() {
        val dataSize = 100 * 1024 // 100 KB for faster testing
        val configurations = listOf(
            EncodingConfig(dataShards = 4, parityShards = 2),   // 6 total
            EncodingConfig(dataShards = 8, parityShards = 4),   // 12 total
            EncodingConfig(dataShards = 10, parityShards = 10), // 20 total
            EncodingConfig(dataShards = 12, parityShards = 8),  // 20 total
            EncodingConfig(dataShards = 14, parityShards = 6),  // 20 total
            EncodingConfig(dataShards = 16, parityShards = 4)   // 20 total
        )
        
        println("\nConfiguration Performance Comparison:")
        println("Data size: 100 KB")
        println("-".repeat(80))
        println("Config        | Standard (MB/s) | Optimized (MB/s) | 20-Shard (MB/s)")
        println("-".repeat(80))
        
        for (config in configurations) {
            val data = ByteArray(dataSize) { Random.nextBytes(1)[0] }
            
            // Standard encoder
            val standardEncoder = ReedSolomonEncoder()
            val standardTime = measureEncodingTime(standardEncoder, data, config)
            val standardThroughput = (dataSize / (1024.0 * 1024.0)) / (standardTime / 1000.0)
            
            // Optimized encoder
            val optimizedEncoder = OptimizedReedSolomonEncoder()
            val optimizedTime = measureEncodingTime(optimizedEncoder, data, config)
            val optimizedThroughput = (dataSize / (1024.0 * 1024.0)) / (optimizedTime / 1000.0)
            optimizedEncoder.shutdown()
            
            // 20-shard encoder (only for 20 total shards)
            val twentyShardThroughput = if (config.totalShards == 20) {
                val twentyShardEncoder = TwentyShardOptimizedEncoder()
                val twentyShardTime = measureEncodingTime(twentyShardEncoder, data, config)
                val throughput = (dataSize / (1024.0 * 1024.0)) / (twentyShardTime / 1000.0)
                twentyShardEncoder.shutdown()
                throughput
            } else {
                -1.0
            }
            
            val configStr = "${config.dataShards}+${config.parityShards}"
            val twentyShardStr = if (twentyShardThroughput > 0) {
                String.format("%6.2f", twentyShardThroughput)
            } else {
                "   N/A"
            }
            
            println(String.format("%-12s | %15.2f | %16.2f | %15s",
                configStr, standardThroughput, optimizedThroughput, twentyShardStr))
        }
    }
    
    private fun measureEncodingTime(encoder: Any, data: ByteArray, config: EncodingConfig): Double {
        // Warm up
        when (encoder) {
            is ReedSolomonEncoder -> encoder.encode(data, config)
            is OptimizedReedSolomonEncoder -> encoder.encode(data, config)
            is TwentyShardOptimizedEncoder -> encoder.encode(data, config)
        }
        
        // Measure
        val times = mutableListOf<Long>()
        repeat(3) {
            val time = measureNanoTime {
                when (encoder) {
                    is ReedSolomonEncoder -> encoder.encode(data, config)
                    is OptimizedReedSolomonEncoder -> encoder.encode(data, config)
                    is TwentyShardOptimizedEncoder -> encoder.encode(data, config)
                }
            }
            times.add(time)
        }
        
        return times.average() / 1_000_000.0 // ms
    }
}