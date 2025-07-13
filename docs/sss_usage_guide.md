# Shamir Secret Sharing (SSS) Usage Guide

## Table of Contents
1. [Introduction](#introduction)
2. [Getting Started](#getting-started)
3. [Basic Usage](#basic-usage)
4. [Advanced Features](#advanced-features)
5. [Best Practices](#best-practices)
6. [Common Use Cases](#common-use-cases)
7. [Error Handling](#error-handling)
8. [Performance Considerations](#performance-considerations)

## Introduction

Shamir Secret Sharing (SSS) is a cryptographic algorithm that splits a secret into multiple shares, where a threshold number of shares is required to reconstruct the original secret. This implementation provides a secure, validated, and user-friendly API for secret sharing operations.

### Key Features
- **Threshold-based reconstruction**: Configure k-of-n sharing (e.g., 3 of 5 shares needed)
- **Cryptographic security**: Uses secure random generation and GF(256) arithmetic
- **Integrity verification**: SHA-256 based tamper detection
- **Comprehensive validation**: Detects corrupted and incompatible shares
- **Memory security**: Automatic cleanup of sensitive data
- **User-friendly API**: Simple methods with robust error handling

## Getting Started

### Installation
The SSS library is part of the `cb.core.tools` package:

```kotlin
import cb.core.tools.sss.ShamirSecretSharing
import cb.core.tools.sss.models.*
```

### Basic Concepts
- **Secret**: The sensitive data you want to protect (up to 1024 bytes)
- **Shares**: Pieces of the secret distributed to different parties
- **Threshold (k)**: Minimum number of shares needed for reconstruction
- **Total Shares (n)**: Total number of shares created
- **Share Metadata**: Information about the secret and sharing configuration

## Basic Usage

### Simple Secret Splitting

```kotlin
// Create the main SSS instance
val sss = ShamirSecretSharing()

// Define the sharing configuration (3 of 5 shares)
val config = SSSConfig(threshold = 3, totalShares = 5)

// Split a string secret
val secret = "My sensitive password"
val splitResult = sss.split(secret, config)

when (splitResult) {
    is SSSResult.Success -> {
        val shares = splitResult.value.shares
        val metadata = splitResult.value.metadata
        
        // Distribute shares to different parties
        shares.forEach { share ->
            println("Share ${share.index}: ${share.toBase64()}")
        }
    }
    is SSSResult.Failure -> {
        println("Failed to split secret: ${splitResult.message}")
    }
}
```

### Secret Reconstruction

```kotlin
// Collect at least threshold shares (3 in this example)
val collectedShares = listOf(share1, share2, share3)

// Reconstruct the secret
val reconstructResult = sss.reconstructString(collectedShares)

when (reconstructResult) {
    is SSSResult.Success -> {
        val reconstructedSecret = reconstructResult.value
        println("Reconstructed: $reconstructedSecret")
    }
    is SSSResult.Failure -> {
        println("Failed to reconstruct: ${reconstructResult.message}")
    }
}
```

### Working with Binary Data

```kotlin
// Split binary data (e.g., encryption keys, files)
val binarySecret = byteArrayOf(0x01, 0x02, 0x03, 0x04)
val splitResult = sss.split(binarySecret, config)

// Reconstruct binary data
val reconstructResult = sss.reconstruct(shares)
val reconstructedBytes = reconstructResult.getOrNull()
```

## Advanced Features

### Share Validation

Before attempting reconstruction, you can validate shares:

```kotlin
val validationResult = sss.validateShares(shares)

when (validationResult) {
    is SSSResult.Success -> {
        // Shares are valid, proceed with reconstruction
        val secret = sss.reconstruct(shares)
    }
    is SSSResult.Failure -> {
        when (validationResult.error) {
            SSSError.INSUFFICIENT_SHARES -> {
                // Need more shares
            }
            SSSError.INVALID_SHARE -> {
                // One or more shares are corrupted
            }
            SSSError.INCOMPATIBLE_SHARES -> {
                // Shares are from different split operations
            }
            else -> {
                // Other validation error
            }
        }
    }
}
```

### Working with Share Subsets

```kotlin
val splitResult = sss.split(secret, config).getOrThrow()

// Get specific shares by index
val shareIndices = listOf(1, 3, 5)
val selectedShares = splitResult.getSharesByIndices(shareIndices)

// Convert to map for easy access
val shareMap = splitResult.toShareMap()
val share3 = shareMap[3]
```

### Share Serialization

Shares can be serialized for storage or transmission:

```kotlin
// Serialize share to Base64
val encodedShare = share.toBase64()

// Store or transmit the encoded share
saveToFile(encodedShare)

// Later, deserialize the share
val decodedShare = SecretShare.fromBase64(encodedShare)
```

### Metadata Validation

Use metadata to ensure shares are compatible:

```kotlin
val splitResult = sss.split(secret, config).getOrThrow()
val metadata = splitResult.metadata

// Later, when reconstructing
val reconstructResult = sss.reconstruct(shares, metadata)

// The metadata ensures:
// - Shares are from the same split operation
// - The reconstructed secret matches the original hash
// - Configuration parameters are consistent
```

## Best Practices

### Security Considerations

1. **Secure Distribution**: Distribute shares through different channels
   ```kotlin
   // Don't send all shares through the same channel
   sendViaEmail(shares[0])
   sendViaSMS(shares[1])
   storeInDatabase(shares[2])
   ```

2. **Access Control**: Limit access to shares based on roles
   ```kotlin
   // Store shares with appropriate access controls
   storeShareForUser(share1, "admin1")
   storeShareForUser(share2, "admin2")
   storeShareForUser(share3, "backup_system")
   ```

3. **Share Rotation**: Periodically re-split secrets with new shares
   ```kotlin
   fun rotateShares(oldShares: List<SecretShare>) {
       // Reconstruct the secret
       val secret = sss.reconstruct(oldShares).getOrThrow()
       
       // Create new shares with updated configuration
       val newConfig = SSSConfig(threshold = 3, totalShares = 5)
       val newShares = sss.split(secret, newConfig).getOrThrow()
       
       // Distribute new shares and revoke old ones
   }
   ```

### Configuration Guidelines

1. **Choosing Threshold and Total Shares**:
   - Higher threshold = better security but less fault tolerance
   - More total shares = better availability but more management overhead
   
   ```kotlin
   // High security: 5 of 7 shares needed
   val highSecurity = SSSConfig(threshold = 5, totalShares = 7)
   
   // High availability: 2 of 5 shares needed
   val highAvailability = SSSConfig(threshold = 2, totalShares = 5)
   
   // Balanced: 3 of 5 shares needed
   val balanced = SSSConfig(threshold = 3, totalShares = 5)
   ```

2. **Secret Size Limits**:
   ```kotlin
   // Check secret size before splitting
   val secret = loadSecretData()
   if (secret.size > SSSConfig.MAX_SECRET_SIZE) {
       // Consider chunking or compression
       val compressed = compress(secret)
       val result = sss.split(compressed, config)
   }
   ```

### Error Handling Best Practices

```kotlin
fun safeSecretReconstruction(shares: List<SecretShare>): String? {
    return try {
        // Validate shares first
        val validationResult = sss.validateShares(shares)
        if (validationResult.isFailure()) {
            logger.warn("Share validation failed: ${validationResult}")
            return null
        }
        
        // Attempt reconstruction
        when (val result = sss.reconstructString(shares)) {
            is SSSResult.Success -> result.value
            is SSSResult.Failure -> {
                logger.error("Reconstruction failed: ${result.message}")
                null
            }
            is SSSResult.PartialReconstruction -> {
                logger.warn("Partial reconstruction: ${result.validShares.size} valid shares")
                null
            }
        }
    } catch (e: Exception) {
        logger.error("Unexpected error during reconstruction", e)
        null
    }
}
```

## Common Use Cases

### 1. Password Recovery System

```kotlin
class PasswordRecoveryService(private val sss: ShamirSecretSharing) {
    
    fun setupRecovery(userId: String, masterPassword: String): List<String> {
        // Create 5 recovery codes, any 3 can recover the password
        val config = SSSConfig(threshold = 3, totalShares = 5)
        val result = sss.split(masterPassword, config).getOrThrow()
        
        // Generate user-friendly recovery codes
        return result.shares.map { share ->
            val encoded = share.toBase64()
            // Format as readable code: XXXX-XXXX-XXXX-XXXX
            formatAsRecoveryCode(encoded)
        }
    }
    
    fun recoverPassword(recoveryCodes: List<String>): String? {
        val shares = recoveryCodes.mapNotNull { code ->
            try {
                val encoded = parseRecoveryCode(code)
                SecretShare.fromBase64(encoded)
            } catch (e: Exception) {
                null
            }
        }
        
        return sss.reconstructString(shares).getOrNull()
    }
}
```

### 2. Distributed Key Management

```kotlin
class DistributedKeyManager(private val sss: ShamirSecretSharing) {
    
    fun distributeEncryptionKey(key: ByteArray, trustees: List<Trustee>): Boolean {
        require(trustees.size >= 5) { "Need at least 5 trustees" }
        
        // Require 3 of 5 trustees to reconstruct the key
        val config = SSSConfig(threshold = 3, totalShares = trustees.size)
        val result = sss.split(key, config)
        
        return when (result) {
            is SSSResult.Success -> {
                // Distribute shares to trustees
                result.value.shares.zip(trustees).forEach { (share, trustee) ->
                    trustee.storeShare(share.toBase64())
                }
                true
            }
            else -> false
        }
    }
    
    fun reconstructKey(trustees: List<Trustee>): ByteArray? {
        // Collect shares from available trustees
        val shares = trustees.mapNotNull { trustee ->
            trustee.getShare()?.let { SecretShare.fromBase64(it) }
        }
        
        return sss.reconstruct(shares).getOrNull()
    }
}
```

### 3. Secure Configuration Management

```kotlin
class SecureConfigManager(private val sss: ShamirSecretSharing) {
    
    fun protectConfiguration(config: String, locations: List<ConfigStore>): ShareMetadata? {
        val sssConfig = SSSConfig(
            threshold = (locations.size / 2) + 1,  // Majority needed
            totalShares = locations.size
        )
        
        val result = sss.split(config, sssConfig)
        
        return when (result) {
            is SSSResult.Success -> {
                // Store shares in different locations
                result.value.shares.zip(locations).forEach { (share, store) ->
                    store.save(share.index, share.toBase64())
                }
                result.value.metadata
            }
            else -> null
        }
    }
}
```

## Error Handling

### Error Types

The SSS library uses a sealed result type with specific error categories:

```kotlin
when (result) {
    is SSSResult.Success -> {
        // Operation succeeded
    }
    is SSSResult.Failure -> {
        when (result.error) {
            SSSError.INVALID_CONFIG -> {
                // Configuration parameters are invalid
            }
            SSSError.INVALID_SECRET -> {
                // Secret size or format is invalid
            }
            SSSError.INVALID_SHARE -> {
                // One or more shares are corrupted
            }
            SSSError.INSUFFICIENT_SHARES -> {
                // Not enough shares for reconstruction
            }
            SSSError.INCOMPATIBLE_SHARES -> {
                // Shares are from different operations
            }
            SSSError.RECONSTRUCTION_FAILED -> {
                // Mathematical reconstruction failed
            }
            else -> {
                // Other errors
            }
        }
    }
    is SSSResult.PartialReconstruction -> {
        // Some shares are invalid but might still reconstruct
        if (result.canReconstruct) {
            // Try with valid shares only
            val validShares = result.validShares
            sss.reconstruct(validShares)
        }
    }
}
```

### Secure Error Messages

Error messages are sanitized to prevent information leakage:

```kotlin
// Safe error messages don't reveal:
// - Specific numeric values
// - Internal implementation details  
// - Secret content or properties
// - Cryptographic parameters

// Example safe error:
"Invalid share format"  // Good

// Example unsafe error:
"Share 3 has invalid coefficient 0x4F at byte 127"  // Bad - reveals details
```

## Performance Considerations

### Benchmarks

Performance varies based on secret size and configuration:

| Secret Size | Shares (k/n) | Split Time | Reconstruct Time |
|------------|--------------|------------|------------------|
| 256 bytes  | 3/5         | ~2 ms      | ~1 ms           |
| 512 bytes  | 3/5         | ~4 ms      | ~2 ms           |
| 1024 bytes | 3/5         | ~8 ms      | ~4 ms           |
| 256 bytes  | 10/20       | ~5 ms      | ~3 ms           |

### Optimization Tips

1. **Reuse SSS Instance**: The ShamirSecretSharing instance is thread-safe
   ```kotlin
   // Good - reuse instance
   class MyService {
       private val sss = ShamirSecretSharing()
       
       fun processSecret(secret: String) {
           sss.split(secret, config)
       }
   }
   ```

2. **Batch Operations**: Process multiple secrets efficiently
   ```kotlin
   fun splitMultipleSecrets(secrets: List<ByteArray>, config: SSSConfig): List<SSSResult<SplitResult>> {
       val sss = ShamirSecretSharing()
       return secrets.map { secret ->
           sss.split(secret, config)
       }
   }
   ```

3. **Memory Management**: Clear sensitive data when done
   ```kotlin
   val secret = loadSensitiveData()
   try {
       val result = sss.split(secret, config)
       // Process result
   } finally {
       // Secret is automatically cleared internally
       // But clear any copies you made
       secret.fill(0)
   }
   ```

## Conclusion

The Shamir Secret Sharing implementation provides a robust, secure, and user-friendly way to protect sensitive data through cryptographic splitting. By following the best practices and examples in this guide, you can effectively integrate secret sharing into your applications for enhanced security and distributed trust.

For security considerations and threat models, see the [SSS Security Guide](sss_security_guide.md).
For integration examples with other systems, see the [SSS Integration Examples](sss_integration_examples.md).