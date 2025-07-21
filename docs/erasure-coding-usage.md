# Reed-Solomon Erasure Coding Library Usage Guide

## Overview

The Reed-Solomon Erasure Coding library in `cb.core.tools.erasure` provides reliable data protection through redundancy. It allows you to encode data into multiple shards, where any subset of shards (equal to the original data size) can reconstruct the full data.

## Key Features

- **Guaranteed reconstruction**: Recovers data from ANY k shards out of n total
- **Matrix-based implementation**: Uses systematic Reed-Solomon with Vandermonde/Cauchy matrices
- **Configurable redundancy**: Choose how many data and parity shards to create
- **Efficient implementation**: Uses GF(256) arithmetic for fast encoding/decoding
- **Streaming support**: Handle large files with memory-efficient streaming APIs
- **Kotlin coroutines**: Full async support for non-blocking operations
- **Robust error handling**: Comprehensive validation and error recovery
- **Non-contiguous shard support**: Handle arbitrary shard combinations and indices
- **Enhanced diagnostics**: Detailed error reporting and performance metrics

## Basic Usage

### Simple Encoding and Decoding

```kotlin
import cb.core.tools.erasure.ReedSolomonEncoder
import cb.core.tools.erasure.ReedSolomonDecoder
import cb.core.tools.erasure.models.EncodingConfig
import cb.core.tools.erasure.models.ReconstructionResult

// Configure encoding parameters
val config = EncodingConfig(
    dataShards = 4,    // Number of data shards
    parityShards = 2   // Number of redundancy shards
)

// Create encoder and decoder
val encoder = ReedSolomonEncoder()
val decoder = ReedSolomonDecoder()

// Encode data
val originalData = "Hello, World!".toByteArray()
val shards = encoder.encode(originalData, config)

// Simulate loss of some shards (keeping minimum required)
val availableShards = shards.take(4)  // Keep only 4 out of 6 shards

// Decode from available shards
when (val result = decoder.decode(availableShards)) {
    is ReconstructionResult.Success -> {
        val reconstructedData = result.data
        println("Reconstructed: ${String(reconstructedData)}")
    }
    is ReconstructionResult.Failure -> {
        println("Failed: ${result.error}")
    }
}

// Advanced: Decode from non-contiguous shards
val nonContiguousShards = listOf(shards[1], shards[2], shards[4], shards[5])
val result2 = decoder.decode(nonContiguousShards) // Works with ANY k shards!
```

### Streaming Large Files

```kotlin
import cb.core.tools.erasure.stream.StreamingEncoder
import cb.core.tools.erasure.stream.StreamingDecoder
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import java.io.File

runBlocking {
    val config = EncodingConfig(dataShards = 8, parityShards = 4)
    
    // Encode a large file
    val encoder = StreamingEncoder()
    val inputFile = File("large-video.mp4")
    
    inputFile.inputStream().use { input ->
        val shardFlow = encoder.encodeStream(input, config)
        val allShards = shardFlow.toList()
        
        // Save shards to files
        allShards.forEachIndexed { index, shard ->
            File("shard_$index.dat").writeBytes(shard.data)
        }
    }
    
    // Later, reconstruct from available shards
    val decoder = StreamingDecoder()
    val availableShards = loadAvailableShards() // Load from disk
    
    val outputFile = File("reconstructed-video.mp4")
    outputFile.outputStream().use { output ->
        decoder.decodeStream(flowOf(availableShards))
            .collect { chunk ->
                output.write(chunk)
            }
    }
}
```

## Configuration Options

### EncodingConfig Parameters

```kotlin
data class EncodingConfig(
    val dataShards: Int,        // Number of data shards (minimum needed for reconstruction)
    val parityShards: Int,      // Number of redundancy shards
    val shardSize: Int = 8192   // Size of each shard in bytes
)
```

**Important constraints:**
- `dataShards` must be > 0
- `parityShards` must be > 0
- `totalShards` (dataShards + parityShards) must be ≤ 256 (GF(256) limitation)

### Choosing Configuration Values

| Use Case | Data Shards | Parity Shards | Notes |
|----------|-------------|---------------|-------|
| High reliability | 4 | 4 | Can lose 50% of shards |
| Balanced | 8 | 4 | Can lose 33% of shards |
| Space efficient | 10 | 2 | Can lose 16% of shards |
| Distributed storage | 16 | 8 | Good for cloud storage |

## Advanced Usage

### Performance-Optimized Encoding

```kotlin
import cb.core.tools.erasure.performance.OptimizedReedSolomonEncoder

// Use optimized encoder for better performance
val encoder = OptimizedReedSolomonEncoder()
try {
    val shards = encoder.encode(data, config)
    // Process shards
} finally {
    encoder.shutdown() // Clean up thread pool
}
```

### Robust Decoding with Validation

```kotlin
import cb.core.tools.erasure.performance.RobustReedSolomonDecoder

val decoder = RobustReedSolomonDecoder()

// Check if reconstruction is possible before attempting
if (decoder.canReconstruct(availableShards, originalConfig)) {
    val result = decoder.decode(availableShards)
    // Handle result
}
```

### Custom Shard Management

```kotlin
// Example: Store shards in different locations
class DistributedShardManager {
    fun distributeShards(shards: List<Shard>) {
        shards.forEach { shard ->
            when {
                shard.isDataShard -> saveToLocalStorage(shard)
                shard.isParityShard -> saveToCloudStorage(shard)
            }
        }
    }
    
    fun gatherAvailableShards(): List<Shard> {
        val localShards = loadFromLocalStorage()
        val cloudShards = loadFromCloudStorage()
        return (localShards + cloudShards).sortedBy { it.index }
    }
}
```

## Error Handling

The library provides detailed error information through `ReconstructionError`:

```kotlin
when (result) {
    is ReconstructionResult.Failure -> {
        when (result.error) {
            ReconstructionError.INSUFFICIENT_SHARDS -> 
                println("Not enough shards available")
            ReconstructionError.CORRUPTED_SHARDS -> 
                println("Checksum verification failed")
            ReconstructionError.INVALID_CONFIGURATION -> 
                println("Shard metadata inconsistent")
            ReconstructionError.MATH_ERROR -> 
                println("Mathematical computation failed")
            ReconstructionError.INCOMPATIBLE_SHARDS -> 
                println("Shards from different encoding operations")
            ReconstructionError.MATRIX_INVERSION_FAILED -> 
                println("Unable to solve linear system for reconstruction")
        }
        // Additional error details in result.message
    }
}
```

### Enhanced Diagnostics

The library now provides detailed reconstruction diagnostics:

```kotlin
when (val result = decoder.decode(availableShards)) {
    is ReconstructionResult.Success -> {
        val diagnostics = result.diagnostics
        println("Strategy used: ${diagnostics?.decodingStrategy}")
        println("Shards used: ${diagnostics?.shardsUsed}")
        println("Performance: ${diagnostics?.performance?.totalTimeMs} ms")
    }
    is ReconstructionResult.Failure -> {
        val diagnostics = result.diagnostics
        println("Failed at stage: ${diagnostics?.failureStage}")
        println("Shards analyzed: ${diagnostics?.shardsAnalyzed}")
    }
}
```

## Performance Considerations

### Memory Usage

- Each shard requires `shardSize` bytes of memory
- Total memory ≈ `dataSize + (totalShards * shardSize)`
- Use streaming APIs for files larger than available memory

### CPU Usage

- Encoding: O(dataSize × parityShards)
- Decoding: O(dataSize × erasures) where erasures ≤ parityShards
- Use `OptimizedReedSolomonEncoder` for parallel processing

### Benchmarks

Typical performance on modern hardware:
- Small data (< 1MB): 50-100 MB/s
- Large data (> 10MB): 100-200 MB/s
- Streaming: Limited by I/O speed
- Non-contiguous shards: Similar performance to contiguous shards

### Performance Characteristics by Shard Pattern

| Shard Pattern | Performance Impact | Notes |
|--------------|-------------------|-------|
| All data shards | Fastest (fast-path) | Direct copy, no computation |
| Contiguous k shards | Fast | Optimized matrix operations |
| Non-contiguous shards | Fast | Full RS decoding, well-optimized |
| Maximum erasures | Slower | More computation required |

## Shard Combination Patterns

The Reed-Solomon implementation guarantees reconstruction from ANY valid combination of k shards:

```kotlin
// Example: 8 data shards + 4 parity shards (total 12), need any 8 to reconstruct
val config = EncodingConfig(8, 4)

// ALL these patterns work:

// Pattern 1: Missing some data shards
val pattern1 = listOf(0, 1, 2, 3, 8, 9, 10, 11) // Missing shards 4-7 ✓

// Pattern 2: Missing all data shards (using only parity)
val pattern2 = listOf(8, 9, 10, 11) + listOf(0, 1, 2, 3) // Parity + some data ✓

// Pattern 3: Non-contiguous selection
val pattern3 = listOf(1, 2, 4, 5, 7, 8, 10, 11) // Missing 0, 3, 6, 9 ✓

// Pattern 4: Every other shard
val pattern4 = (0..11).filter { it % 2 == 0 } + listOf(1, 3) // Even shards + 2 odd ✓

// All patterns work equally well!
val result = decoder.decode(shardsMatchingPattern)
```

### Optimal Shard Selection Strategy

When you have more than k shards available, consider these strategies:

1. **Prefer data shards**: If all data shards are available, use them for fastest decoding
2. **Minimize gaps**: Contiguous shards may have slightly better cache performance
3. **Load balance**: Distribute reads across storage locations
4. **Prioritize reliability**: Keep extra shards as backup during reconstruction

## Best Practices

1. **Choose appropriate shard counts**: Balance between reliability and storage overhead
2. **Use streaming for large files**: Prevents memory exhaustion
3. **Verify checksums**: Always check reconstruction success
4. **Store metadata separately**: Keep shard configuration for recovery
5. **Test recovery scenarios**: Ensure your configuration meets reliability needs
6. **Handle any shard combination**: The decoder now supports all valid k-of-n combinations
7. **Monitor performance**: Use diagnostics to track reconstruction efficiency

## Example: Complete File Protection System

```kotlin
class FileProtectionSystem(
    private val config: EncodingConfig = EncodingConfig(
        dataShards = 8,
        parityShards = 4
    )
) {
    private val encoder = ReedSolomonEncoder()
    private val decoder = ReedSolomonDecoder()
    
    fun protectFile(inputPath: String, outputDir: String) {
        val file = File(inputPath)
        val data = file.readBytes()
        
        // Encode file
        val shards = encoder.encode(data, config)
        
        // Save shards and metadata
        val metadataFile = File(outputDir, "metadata.json")
        metadataFile.writeText(Json.encodeToString(
            FileMetadata(
                originalName = file.name,
                originalSize = file.length(),
                config = config,
                checksum = shards.first().metadata.checksum
            )
        ))
        
        shards.forEachIndexed { index, shard ->
            File(outputDir, "shard_$index.dat").writeBytes(shard.data)
        }
    }
    
    fun recoverFile(shardDir: String, outputPath: String): Boolean {
        // Load metadata
        val metadata = Json.decodeFromString<FileMetadata>(
            File(shardDir, "metadata.json").readText()
        )
        
        // Load available shards
        val shards = File(shardDir).listFiles { file ->
            file.name.startsWith("shard_") && file.name.endsWith(".dat")
        }?.mapNotNull { file ->
            val index = file.name.removePrefix("shard_").removeSuffix(".dat").toIntOrNull()
            index?.let {
                Shard(
                    index = it,
                    data = file.readBytes(),
                    metadata = ShardMetadata(
                        originalSize = metadata.originalSize,
                        config = metadata.config,
                        checksum = metadata.checksum
                    )
                )
            }
        } ?: emptyList()
        
        // Attempt recovery
        return when (val result = decoder.decode(shards)) {
            is ReconstructionResult.Success -> {
                File(outputPath).writeBytes(result.data)
                true
            }
            else -> false
        }
    }
}
```

## Troubleshooting Guide

### Common Issues and Solutions

1. **"CORRUPTED_SHARDS" error with valid shards**
   - **Cause**: Checksum verification failed
   - **Solution**: Ensure shards haven't been modified during storage/transmission
   - **Prevention**: Use secure storage and verify checksums regularly

2. **"INSUFFICIENT_SHARDS" error**
   - **Cause**: Not enough shards available for reconstruction (need at least k)
   - **Solution**: Gather more shards from backup locations
   - **Prevention**: Store shards redundantly across multiple locations

3. **"MATRIX_INVERSION_FAILED" error**
   - **Cause**: Linear system cannot be solved (extremely rare)
   - **Solution**: Verify shards are from the same encoding operation
   - **Prevention**: Don't mix shards from different encoding operations

4. **Performance considerations**
   - **Standard encoding**: 25-50 MB/s typical
   - **Optimized encoding**: 25-175 MB/s with parallelization
   - **Tip**: Use `OptimizedReedSolomonEncoder` for large data sets

### Migration Guide

If you're upgrading from a previous version:

1. **API Compatibility**: All existing APIs remain unchanged
2. **Behavior Changes**: 
   - Shard combinations that previously failed now work
   - Error messages are more detailed
   - Performance metrics are available in results
3. **No Code Changes Required**: Existing code will work without modification
4. **New Features Available**: 
   - Access diagnostics through `result.diagnostics`
   - Use advanced decoding options in `EncodingConfig`

## Mathematical Background

The Reed-Solomon implementation uses systematic encoding based on:

- **Vandermonde or Cauchy matrices** for encoding
- **Matrix inversion** in GF(256) for solving linear systems
- **Gaussian elimination** for arbitrary shard combinations
- **Galois Field arithmetic** for all mathematical operations

This approach guarantees reconstruction from ANY k valid shards out of n total, providing true Reed-Solomon erasure coding properties.

## Conclusion

The Reed-Solomon Erasure Coding library provides a robust solution for data protection through redundancy. Using systematic matrix-based encoding, it guarantees reconstruction from ANY k valid shards out of n total, ensuring you can confidently build distributed storage systems, protect important files, or implement fault-tolerant data transmission with excellent performance and mathematical reliability.