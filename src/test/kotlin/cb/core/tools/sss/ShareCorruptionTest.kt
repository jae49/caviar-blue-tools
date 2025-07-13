package cb.core.tools.sss

import cb.core.tools.sss.models.SSSConfig
import cb.core.tools.sss.models.SecretShare
import cb.core.tools.sss.models.ShareMetadata
import cb.core.tools.sss.models.SSSResult
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Assertions.*

/**
 * Tests for share corruption detection.
 * 
 * NOTE: Many of these tests are currently disabled because they require
 * implementation of hash-based integrity checking in the core SSS classes.
 * These tests serve as a specification for Phase 4 implementation.
 */
class ShareCorruptionTest {
    
    private val sss = ShamirSecretSharing()
    
    @Test
    fun `reconstruct should fail with bit-flipped share data`() {
        val config = SSSConfig(3, 5)
        val secret = "Test corruption detection".toByteArray()
        
        val splitResult = sss.split(secret, config)
        assertTrue(splitResult is SSSResult.Success)
        
        val shares = (splitResult as SSSResult.Success).value.shares.toMutableList()
        
        // Flip a bit in the first share's data
        val corruptedShare = shares[0].copy(
            data = shares[0].data.copyOf().apply {
                this[0] = (this[0].toInt() xor 0x01).toByte()
            }
        )
        shares[0] = corruptedShare
        
        // Reconstruction should fail due to mismatched hash
        val reconstructResult = sss.reconstruct(shares.take(3))
        assertFalse(reconstructResult is SSSResult.Success)
        assertTrue(reconstructResult is SSSResult.Failure)
        val error = reconstructResult as SSSResult.Failure
        assertTrue(error.message.contains("Invalid share", ignoreCase = true))
    }
    
    @Test
    fun `reconstruct should fail with byte-substituted share data`() {
        val config = SSSConfig(3, 5)
        val secret = "Byte substitution test".toByteArray()
        
        val splitResult = sss.split(secret, config)
        assertTrue(splitResult is SSSResult.Success)
        
        val shares = (splitResult as SSSResult.Success).value.shares.toMutableList()
        
        // Substitute multiple bytes in a share
        val corruptedData = shares[1].data.copyOf()
        for (i in 0 until minOf(5, corruptedData.size)) {
            corruptedData[i] = ((corruptedData[i] + 1) % 256).toByte()
        }
        
        shares[1] = shares[1].copy(data = corruptedData)
        
        // Reconstruction should fail
        val reconstructResult = sss.reconstruct(shares.take(3))
        assertFalse(reconstructResult is SSSResult.Success)
    }
    
    @Test
    fun `reconstruct should fail with tampered share index`() {
        val config = SSSConfig(3, 5)
        val secret = "Index tampering test".toByteArray()
        
        val splitResult = sss.split(secret, config)
        assertTrue(splitResult is SSSResult.Success)
        
        val shares = (splitResult as SSSResult.Success).value.shares.toMutableList()
        
        // Create a share with tampered index but keep the same data
        val tamperedShare = SecretShare(
            index = 99, // Changed from original
            data = shares[0].data,
            metadata = shares[0].metadata
        )
        shares[0] = tamperedShare
        
        // Reconstruction might succeed but produce wrong result
        val reconstructResult = sss.reconstruct(shares.take(3))
        if (reconstructResult is SSSResult.Success) {
            val reconstructed = reconstructResult.value
            assertFalse(secret.contentEquals(reconstructed))
        }
    }
    
    @Test
    fun `reconstruct should fail with shares from different split operations`() {
        val config = SSSConfig(3, 5)
        val secret1 = "First secret".toByteArray()
        val secret2 = "Second secret".toByteArray()
        
        val splitResult1 = sss.split(secret1, config)
        val splitResult2 = sss.split(secret2, config)
        
        assertTrue(splitResult1 is SSSResult.Success)
        assertTrue(splitResult2 is SSSResult.Success)
        
        val shares1 = (splitResult1 as SSSResult.Success).value.shares
        val shares2 = (splitResult2 as SSSResult.Success).value.shares
        
        // Mix shares from different operations
        val mixedShares = listOf(shares1[0], shares1[1], shares2[2])
        
        val reconstructResult = sss.reconstruct(mixedShares)
        assertFalse(reconstructResult is SSSResult.Success)
        assertTrue(reconstructResult is SSSResult.Failure)
        val error = reconstructResult as SSSResult.Failure
        assertTrue(error.message.contains("Incompatible", ignoreCase = true))
    }
    
    @Test
    fun `reconstruct should fail with corrupted metadata`() {
        val config = SSSConfig(3, 5)
        val secret = "Metadata corruption test".toByteArray()
        
        val splitResult = sss.split(secret, config)
        assertTrue(splitResult is SSSResult.Success)
        
        val shares = (splitResult as SSSResult.Success).value.shares.toMutableList()
        
        // Corrupt the metadata with proper hash size for init validation
        val corruptedMetadata = ShareMetadata(
            threshold = shares[0].metadata.threshold,
            totalShares = shares[0].metadata.totalShares,
            secretSize = shares[0].metadata.secretSize,
            secretHash = ByteArray(32) { 0xFF.toByte() }, // Wrong hash but correct size
            timestamp = shares[0].metadata.timestamp,
            shareSetId = "corrupted_id"
        )
        
        shares[0] = SecretShare(
            index = shares[0].index,
            data = shares[0].data,
            metadata = corruptedMetadata,
            dataHash = shares[0].dataHash // Keep original hash to pass init validation
        )
        
        val reconstructResult = sss.reconstruct(shares.take(3))
        assertFalse(reconstructResult is SSSResult.Success)
    }
    
    @Test
    fun `reconstruct should fail with mismatched secret sizes in metadata`() {
        val config = SSSConfig(3, 5)
        val secret = "Size mismatch test".toByteArray()
        
        val splitResult = sss.split(secret, config)
        assertTrue(splitResult is SSSResult.Success)
        
        val shares = (splitResult as SSSResult.Success).value.shares.toMutableList()
        
        // Create metadata with mismatched secret size by extending data to match
        val originalData = shares[1].data
        val extendedData = originalData + ByteArray(10) { 0.toByte() }
        val corruptedMetadata = shares[1].metadata.copy(
            secretSize = shares[1].metadata.secretSize + 10
        )
        shares[1] = SecretShare(
            index = shares[1].index,
            data = extendedData,
            metadata = corruptedMetadata,
            dataHash = shares[1].dataHash // Keep original hash to make integrity check fail
        )
        
        val reconstructResult = sss.reconstruct(shares.take(3))
        assertFalse(reconstructResult is SSSResult.Success)
    }
    
    @Test
    fun `reconstruct should fail with truncated share data`() {
        val config = SSSConfig(3, 5)
        val secret = "Truncation test with some data".toByteArray()
        
        val splitResult = sss.split(secret, config)
        assertTrue(splitResult is SSSResult.Success)
        
        val shares = (splitResult as SSSResult.Success).value.shares.toMutableList()
        
        // Truncate the data of one share while keeping original metadata to bypass init validation
        val truncatedData = shares[0].data.sliceArray(0 until shares[0].data.size / 2)
        val originalMetadata = shares[0].metadata.copy(secretSize = truncatedData.size)
        shares[0] = SecretShare(
            index = shares[0].index,
            data = truncatedData,
            metadata = originalMetadata,
            dataHash = shares[0].dataHash // Keep original hash to pass init validation
        )
        
        val reconstructResult = sss.reconstruct(shares.take(3))
        assertFalse(reconstructResult is SSSResult.Success)
    }
    
    @Test
    fun `reconstruct should fail with extended share data`() {
        val config = SSSConfig(3, 5)
        val secret = "Extension test".toByteArray()
        
        val splitResult = sss.split(secret, config)
        assertTrue(splitResult is SSSResult.Success)
        
        val shares = (splitResult as SSSResult.Success).value.shares.toMutableList()
        
        // Extend the data of one share while keeping original metadata to bypass init validation
        val extendedData = shares[2].data + byteArrayOf(0xFF.toByte(), 0xFF.toByte())
        val originalMetadata = shares[2].metadata.copy(secretSize = extendedData.size)
        shares[2] = SecretShare(
            index = shares[2].index,
            data = extendedData,
            metadata = originalMetadata,
            dataHash = shares[2].dataHash // Keep original hash to pass init validation
        )
        
        val reconstructResult = sss.reconstruct(shares.take(3))
        assertFalse(reconstructResult is SSSResult.Success)
    }
    
    @Test
    fun `handle single corrupted share when extras available`() {
        val config = SSSConfig(3, 6)
        val secret = "Recovery with extras".toByteArray()
        
        val splitResult = sss.split(secret, config)
        assertTrue(splitResult is SSSResult.Success)
        
        val shares = (splitResult as SSSResult.Success).value.shares.toMutableList()
        
        // Corrupt one share
        val corruptedData = shares[0].data.copyOf()
        corruptedData[0] = (corruptedData[0] + 1).toByte()
        shares[0] = shares[0].copy(data = corruptedData)
        
        // Try reconstruction with shares 1-3 (skipping corrupted share 0)
        val reconstructResult = sss.reconstruct(shares.subList(1, 4))
        assertTrue(reconstructResult is SSSResult.Success)
        
        val reconstructed = (reconstructResult as SSSResult.Success).value
        assertArrayEquals(secret, reconstructed)
    }
    
    @Test
    fun `multiple bit errors should be detected`() {
        val config = SSSConfig(3, 5)
        val secret = "Multiple bit error test".toByteArray()
        
        val splitResult = sss.split(secret, config)
        assertTrue(splitResult is SSSResult.Success)
        
        val shares = (splitResult as SSSResult.Success).value.shares.toMutableList()
        
        // Introduce multiple bit errors
        val corruptedData = shares[0].data.copyOf()
        corruptedData[0] = (corruptedData[0].toInt() xor 0xFF).toByte()
        corruptedData[1] = (corruptedData[1].toInt() xor 0xAA).toByte()
        corruptedData[2] = (corruptedData[2].toInt() xor 0x55).toByte()
        
        shares[0] = shares[0].copy(data = corruptedData)
        
        val reconstructResult = sss.reconstruct(shares.take(3))
        assertFalse(reconstructResult is SSSResult.Success)
    }
    
    @Test
    fun `reconstruct should fail with reordered bytes in share`() {
        val config = SSSConfig(3, 5)
        val secret = "Byte reordering test".toByteArray()
        
        val splitResult = sss.split(secret, config)
        assertTrue(splitResult is SSSResult.Success)
        
        val shares = (splitResult as SSSResult.Success).value.shares.toMutableList()
        
        // Reorder bytes in a share
        val reorderedData = shares[0].data.copyOf()
        if (reorderedData.size >= 4) {
            val temp = reorderedData[0]
            reorderedData[0] = reorderedData[1]
            reorderedData[1] = reorderedData[2]
            reorderedData[2] = reorderedData[3]
            reorderedData[3] = temp
        }
        
        shares[0] = shares[0].copy(data = reorderedData)
        
        val reconstructResult = sss.reconstruct(shares.take(3))
        assertFalse(reconstructResult is SSSResult.Success)
    }
    
    @Test
    fun `reconstruct should fail with all shares having same minor corruption`() {
        val config = SSSConfig(3, 5)
        val secret = "Systematic corruption test".toByteArray()
        
        val splitResult = sss.split(secret, config)
        assertTrue(splitResult is SSSResult.Success)
        
        val shares = (splitResult as SSSResult.Success).value.shares.map { share ->
            // Apply same corruption to all shares
            share.copy(
                data = share.data.copyOf().apply {
                    if (size > 0) this[0] = (this[0] + 1).toByte()
                }
            )
        }
        
        val reconstructResult = sss.reconstruct(shares.take(3))
        assertFalse(reconstructResult is SSSResult.Success)
    }
}