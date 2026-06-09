# Caviar Blue Tools

A comprehensive Kotlin library providing enterprise-grade cryptographic and data protection tools, including Reed-Solomon Erasure Coding and Shamir Secret Sharing implementations.

## Features

### Reed-Solomon Erasure Coding (RSEC)
High-performance erasure coding library with systematic matrix-based implementation:
- **Guaranteed reconstruction**: Recovers data from ANY k valid shards out of n total
- **MDS algorithm**: Cauchy generator matrix in GF(256) — every k-of-n subset is recoverable
- **Configurable redundancy**: Support for up to 256 total shards (data + parity)
- **High throughput**: ~100–450 MB/s encode, ~80–330 MB/s decode (table-based region multiply)
- **Streaming support**: Memory-efficient processing of large files with Kotlin coroutines
- **Complete reliability**: No shard combination limitations

### Shamir Secret Sharing (SSS)
Cryptographically secure secret splitting and reconstruction:
- **Threshold sharing**: Split secrets into n shares, requiring k shares to reconstruct
- **Tamper detection**: SHA-256 integrity checking on all shares
- **Attack resistance**: Protection against 9+ known attack vectors
- **Secure memory handling**: Multi-pass clearing of sensitive data
- **Flexible size support**: Secrets up to 1024 bytes, up to 128 shares

### Application Settings System
Database-backed configuration management with type-safe APIs:
- **Application metadata**: Store app name, version, and schema version
- **Runtime properties**: Add/retrieve/delete settings with automatic type conversion
- **Multiple data types**: String, Int, Long, Double, Boolean, and JSON support
- **Generic SQL**: Compatible with PostgreSQL, SQLite, and SQL Server
- **Type safety**: Shields developers from manual type conversions
- **Transaction support**: Atomic operations with automatic rollback

## Installation

Add to your Gradle dependencies:

```kotlin
dependencies {
    implementation("cb.core:caviar-blue-tools:1.1.5")
}
```

## Quick Start

### Reed-Solomon Erasure Coding

```kotlin
import cb.core.tools.erasure.*
import cb.core.tools.erasure.models.EncodingConfig

// Configure encoding (8 data shards + 6 parity shards)
val config = EncodingConfig(
    dataShards = 8, 
    parityShards = 6
)
val encoder = ReedSolomonEncoder()
val decoder = ReedSolomonDecoder()

// Encode data
val data = "Your important data".toByteArray()
val shards = encoder.encode(data, config)

// Lose some shards (up to 6 can be lost)
// Any combination of 8 shards will work - guaranteed!
val availableShards = shards.filterIndexed { i, _ -> i != 0 && i != 3 && i != 6 }

// Reconstruct original data from ANY 8 shards
when (val result = decoder.decode(availableShards)) {
    is ReconstructionResult.Success -> {
        val reconstructedData = result.data
        println("Successfully reconstructed!")
    }
    is ReconstructionResult.Failure -> {
        println("Failed: ${result.error}")
    }
}
```

### Shamir Secret Sharing

```kotlin
import cb.core.tools.sss.ShamirSecretSharing
import cb.core.tools.sss.models.SSSConfig

// Create SSS instance
val sss = ShamirSecretSharing()

// Configure sharing (3-of-5 threshold)
val config = SSSConfig(threshold = 3, totalShares = 5)

// Split secret
val secret = "confidential-api-key-12345"
val shares = sss.splitString(secret, config).getOrThrow()

// Distribute shares to different parties...
// Later: collect any 3 shares to reconstruct
val collectedShares = shares.take(3)
val reconstructed = sss.reconstructString(collectedShares).getOrThrow()
```

### Application Settings System

```kotlin
import cb.core.tools.settings.ApplicationSettingsManager
import cb.core.tools.settings.database.SimpleConnectionWrapper

// Setup database connection
val connection = DriverManager.getConnection("jdbc:sqlite:settings.db")
val settingsManager = ApplicationSettingsManager(SimpleConnectionWrapper(connection))

// Initialize schema
settingsManager.initializeSchema()

// Register application
settingsManager.registerApplication("MyApp", "1.0.0", "1.0")

// Store typed settings
settingsManager.setString("app.name", "My Application")
settingsManager.setInt("max.connections", 100)
settingsManager.setBoolean("debug.enabled", true)
settingsManager.setJson("db.config", """{"host":"localhost","port":5432}""")

// Retrieve with type safety and defaults
val appName = settingsManager.getString("app.name").getOrNull()
val maxConn = settingsManager.getInt("max.connections", 50).getOrNull() ?: 50
val debugMode = settingsManager.getBoolean("debug.enabled", false).getOrNull() ?: false
```

## Advanced Features

### Space-Efficient and Streaming Erasure Coding

```kotlin
val config = EncodingConfig(
    dataShards = 10,
    parityShards = 6
)

// Space-efficient encoder: dynamic shard sizing to minimize padding overhead
val spaceEfficientEncoder = SpaceEfficientReedSolomonEncoder()
val shards = spaceEfficientEncoder.encode(largeData, config)

// Stream large files
val streamEncoder = StreamingEncoder()
val shardFlow = streamEncoder.encodeStream(inputStream, config)
```

### Secure Secret Sharing with Validation

```kotlin
// Split with custom description
val splitResult = sss.split(
    secret = sensitiveData,
    config = config,
    description = "Production DB credentials"
)

// Validate shares before reconstruction
val shares = splitResult.getOrThrow().shares
shares.forEach { share ->
    println("Share ${share.index}: ${share.toBase64()}")
    // Each share contains integrity hash for tamper detection
}
```

## Performance

### Reed-Solomon Benchmarks (measured, 256 KB, maximum erasures)
- **Encoding**: ~100–450 MB/s depending on configuration
- **Decoding**: ~80–330 MB/s depending on configuration and erasure count
- **Reliability**: 100% reconstruction guarantee from ANY k shards (Cauchy/MDS)

### Shamir Secret Sharing
- **Splitting**: ~1ms for 1KB secret with 10 shares
- **Reconstruction**: <1ms for threshold shares
- **Validation overhead**: <5% performance impact

## Security Features

- **Cryptographically secure randomness** for all operations
- **Tamper detection** with SHA-256 integrity checking
- **Memory security** with multi-pass clearing
- **Side-channel resistance** in critical operations
- **Comprehensive validation** at every layer

## Testing

```bash
# Run fast tests (<30 seconds)
gradle test

# Run performance benchmarks and stress tests
gradle slowTests

# Run all tests
gradle test slowTests
```

## Documentation

- [Reed-Solomon Usage Guide](docs/erasure-coding-usage.md)
- [Reed-Solomon API Reference](docs/erasure-coding-api.md)
- [Shamir Secret Sharing Usage Guide](docs/sss_usage_guide.md)
- [SSS Security Guide](docs/sss_security_guide.md)
- [SSS Integration Examples](docs/sss_integration_examples.md)
- [Application Settings Usage Guide](docs/settings-usage-guide.md)

## Requirements

- Kotlin 2.4+
- JDK 25 (the build uses a `jvmToolchain(25)`)
- Gradle 9+

## License

Copyright © 2025 John Engelman

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.

## Contributing

Please read our contributing guidelines before submitting pull requests.

## Support

For issues and feature requests, please use the GitHub issue tracker.