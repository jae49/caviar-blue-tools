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

package cb.core.tools.sss.models

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.Assertions

class SSSResultTest {

    @Test
    fun `should create and handle success result`() {
        val result = SSSResult.success("test value")
        
        Assertions.assertTrue(result.isSuccess())
        Assertions.assertFalse(result.isFailure())
        Assertions.assertEquals("test value", result.getOrNull())
        Assertions.assertEquals("test value", result.getOrThrow())
        
        Assertions.assertTrue(result is SSSResult.Success)
        Assertions.assertEquals("test value", (result as SSSResult.Success).value)
    }

    @Test
    fun `should create and handle failure result`() {
        val exception = IllegalArgumentException("test error")
        val result = SSSResult.failure(
            SSSError.INVALID_CONFIG,
            "Configuration is invalid",
            exception
        )
        
        Assertions.assertFalse(result.isSuccess())
        Assertions.assertTrue(result.isFailure())
        Assertions.assertNull(result.getOrNull())
        
        Assertions.assertTrue(result is SSSResult.Failure)
        val failure = result as SSSResult.Failure
        Assertions.assertEquals(SSSError.INVALID_CONFIG, failure.error)
        Assertions.assertEquals("Configuration is invalid", failure.message)
        Assertions.assertEquals(exception, failure.cause)
        
        val thrown = assertThrows<SSSException> {
            result.getOrThrow()
        }
        Assertions.assertEquals("Configuration is invalid", thrown.message)
        Assertions.assertEquals(exception, thrown.cause)
    }

    @Test
    fun `should create and handle partial reconstruction result`() {
        val metadata = ShareMetadata(
            threshold = 3,
            totalShares = 5,
            secretSize = 10,
            secretHash = ByteArray(32)
        )
        
        val validShares = listOf(
            SecretShare(1, ByteArray(10), metadata),
            SecretShare(2, ByteArray(10), metadata)
        )
        
        val invalidShares = listOf(
            InvalidShare(
                SecretShare(3, ByteArray(10), metadata),
                "Corrupted data"
            )
        )
        
        val result = SSSResult.partialReconstruction(validShares, invalidShares, threshold = 3)
        
        Assertions.assertFalse(result.isSuccess())
        Assertions.assertFalse(result.isFailure())
        Assertions.assertNull(result.getOrNull())
        
        Assertions.assertTrue(result is SSSResult.PartialReconstruction)
        val partial = result as SSSResult.PartialReconstruction
        Assertions.assertEquals(2, partial.validShares.size)
        Assertions.assertEquals(1, partial.invalidShares.size)
        Assertions.assertFalse(partial.canReconstruct)
        Assertions.assertEquals(1, partial.missingShareCount)
        
        assertThrows<SSSException> {
            result.getOrThrow()
        }.also {
            Assertions.assertTrue(it.message!!.contains("Partial reconstruction"))
            Assertions.assertTrue(it.message!!.contains("2 valid shares"))
            Assertions.assertTrue(it.message!!.contains("1 invalid shares"))
            Assertions.assertTrue(it.message!!.contains("1 shares needed"))
        }
    }

    @Test
    fun `should handle partial reconstruction with enough shares`() {
        val metadata = ShareMetadata(
            threshold = 2,
            totalShares = 5,
            secretSize = 10,
            secretHash = ByteArray(32)
        )
        
        val validShares = listOf(
            SecretShare(1, ByteArray(10), metadata),
            SecretShare(2, ByteArray(10), metadata),
            SecretShare(4, ByteArray(10), metadata)
        )
        
        val invalidShares = listOf(
            InvalidShare(SecretShare(3, ByteArray(10), metadata), "Corrupted"),
            InvalidShare(SecretShare(5, ByteArray(10), metadata), "Missing")
        )
        
        val result = SSSResult.partialReconstruction(validShares, invalidShares, threshold = 2)
        
        Assertions.assertTrue(result is SSSResult.PartialReconstruction)
        val partial = result as SSSResult.PartialReconstruction
        Assertions.assertTrue(partial.canReconstruct)
        Assertions.assertEquals(0, partial.missingShareCount)
    }

    @Test
    fun `should map success results`() {
        val result = SSSResult.success(5)
        val mapped = result.map { it * 2 }
        
        Assertions.assertTrue(mapped.isSuccess())
        Assertions.assertEquals(10, mapped.getOrNull())
    }

    @Test
    fun `should propagate failure in map`() {
        val result: SSSResult<Int> = SSSResult.failure(
            SSSError.INVALID_SECRET,
            "Secret too large"
        )
        val mapped = result.map { it * 2 }
        
        Assertions.assertTrue(mapped.isFailure())
        Assertions.assertNull(mapped.getOrNull())
        Assertions.assertTrue(mapped is SSSResult.Failure)
        Assertions.assertEquals(SSSError.INVALID_SECRET, (mapped as SSSResult.Failure).error)
    }

    @Test
    fun `should handle partial reconstruction in map`() {
        val metadata = ShareMetadata(
            threshold = 3,
            totalShares = 5,
            secretSize = 10,
            secretHash = ByteArray(32)
        )
        
        val result: SSSResult<Int> = SSSResult.partialReconstruction(
            listOf(SecretShare(1, ByteArray(10), metadata)),
            emptyList(),
            threshold = 3
        )
        
        val mapped = result.map { it * 2 }
        
        Assertions.assertTrue(mapped.isFailure())
        Assertions.assertTrue(mapped is SSSResult.Failure)
        Assertions.assertEquals(SSSError.PARTIAL_DATA, (mapped as SSSResult.Failure).error)
    }

    @Test
    fun `should flatMap success results`() {
        val result = SSSResult.success(5)
        val flatMapped = result.flatMap { 
            if (it > 0) SSSResult.success(it.toString())
            else SSSResult.failure(SSSError.INVALID_CONFIG, "Negative value")
        }
        
        Assertions.assertTrue(flatMapped.isSuccess())
        Assertions.assertEquals("5", flatMapped.getOrNull())
    }

    @Test
    fun `should propagate failure in flatMap`() {
        val result: SSSResult<Int> = SSSResult.failure(
            SSSError.INSUFFICIENT_SHARES,
            "Not enough shares"
        )
        val flatMapped = result.flatMap { SSSResult.success(it.toString()) }
        
        Assertions.assertTrue(flatMapped.isFailure())
        Assertions.assertTrue(flatMapped is SSSResult.Failure)
        Assertions.assertEquals(SSSError.INSUFFICIENT_SHARES, (flatMapped as SSSResult.Failure).error)
    }

    @Test
    fun `should chain flatMap operations`() {
        val result = SSSResult.success(10)
            .flatMap { 
                if (it > 5) SSSResult.success(it / 2)
                else SSSResult.failure(SSSError.INVALID_CONFIG, "Too small")
            }
            .flatMap {
                if (it % 2 == 0) SSSResult.success("Even: $it")
                else SSSResult.success("Odd: $it")
            }
        
        Assertions.assertTrue(result.isSuccess())
        Assertions.assertEquals("Odd: 5", result.getOrNull())
    }

    @Test
    fun `should create result from nullable`() {
        val nonNull: String? = "test"
        val result1 = SSSResult.fromNullable(
            nonNull,
            SSSError.INVALID_CONFIG,
            "Value was null"
        )
        
        Assertions.assertTrue(result1.isSuccess())
        Assertions.assertEquals("test", result1.getOrNull())
        
        val nullValue: String? = null
        val result2 = SSSResult.fromNullable(
            nullValue,
            SSSError.INVALID_CONFIG,
            "Value was null"
        )
        
        Assertions.assertTrue(result2.isFailure())
        Assertions.assertTrue(result2 is SSSResult.Failure)
        Assertions.assertEquals("Value was null", (result2 as SSSResult.Failure).message)
    }

    @Test
    fun `should catch exceptions in catching block`() {
        val result1 = SSSResult.catching {
            "success"
        }
        
        Assertions.assertTrue(result1.isSuccess())
        Assertions.assertEquals("success", result1.getOrNull())
        
        val result2 = SSSResult.catching {
            throw SSSException("SSS error", error = SSSError.RECONSTRUCTION_FAILED)
        }
        
        Assertions.assertTrue(result2.isFailure())
        Assertions.assertTrue(result2 is SSSResult.Failure)
        val failure = result2 as SSSResult.Failure
        Assertions.assertEquals(SSSError.RECONSTRUCTION_FAILED, failure.error)
        Assertions.assertEquals("SSS error", failure.message)
        
        val result3 = SSSResult.catching {
            throw IllegalArgumentException("Generic error")
        }
        
        Assertions.assertTrue(result3.isFailure())
        Assertions.assertTrue(result3 is SSSResult.Failure)
        Assertions.assertEquals(SSSError.UNKNOWN, (result3 as SSSResult.Failure).error)
    }

    @Test
    fun `should handle SSS exception correctly`() {
        val cause = IllegalStateException("root cause")
        val exception = SSSException(
            "Test exception",
            cause,
            SSSError.INCOMPATIBLE_SHARES
        )
        
        Assertions.assertEquals("Test exception", exception.message)
        Assertions.assertEquals(cause, exception.cause)
        Assertions.assertEquals(SSSError.INCOMPATIBLE_SHARES, exception.error)
    }

    @Test
    fun `should create meaningful invalid share`() {
        val metadata = ShareMetadata(
            threshold = 2,
            totalShares = 3,
            secretSize = 10,
            secretHash = ByteArray(32)
        )
        val share = SecretShare(1, ByteArray(10), metadata)
        
        val invalid = InvalidShare(share, "Checksum mismatch")
        
        Assertions.assertEquals(share, invalid.share)
        Assertions.assertEquals("Checksum mismatch", invalid.reason)
    }
}