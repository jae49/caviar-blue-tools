# Reed-Solomon Erasure Coding Library Usage Guide

## Overview

The Reed-Solomon Erasure Coding library in `cb.core.tools.erasure` provides reliable data protection through redundancy. It allows you to encode data into multiple shards, where any subset of shards (equal to the original data size) can reconstruct the full data.

## Key Features

- **Configurable redundancy**: Choose how many data and parity shards to create
- **Efficient implementation**: Uses GF(256) arithmetic for fast encoding/decoding
- **Streaming support**: Handle large files with memory-efficient streaming APIs
- **Kotlin coroutines**: Full async support for non-blocking operations
- **Robust error handling**: Comprehensive validation and error recovery

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
        }
        // Additional error details in result.message
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

## Best Practices

1. **Choose appropriate shard counts**: Balance between reliability and storage overhead
2. **Use streaming for large files**: Prevents memory exhaustion
3. **Verify checksums**: Always check reconstruction success
4. **Store metadata separately**: Keep shard configuration for recovery
5. **Test recovery scenarios**: Ensure your configuration meets reliability needs

## Example: Complete File Protection System

```kotlin
class FileProtectionSystem(
    private val config: EncodingConfig = EncodingConfig(8, 4)
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

## Conclusion

The Reed-Solomon Erasure Coding library provides a robust solution for data protection through redundancy. Whether you're building a distributed storage system, protecting important files, or implementing fault-tolerant data transmission, this library offers the tools you need with excellent performance and reliability.