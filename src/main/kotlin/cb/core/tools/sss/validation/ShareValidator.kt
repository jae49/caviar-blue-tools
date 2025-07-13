package cb.core.tools.sss.validation

import cb.core.tools.sss.models.*

/**
 * Validates shares for integrity and consistency before reconstruction.
 */
object ShareValidator {
    
    /**
     * Validates a single share for integrity.
     */
    fun validateShare(share: SecretShare): SSSResult<SecretShare> {
        // Verify the integrity hash
        if (!share.verifyIntegrity()) {
            return SSSResult.Failure(SSSError.INVALID_SHARE, "Share integrity check failed: data hash mismatch for share ${share.index}")
        }
        
        // Validate metadata consistency
        if (share.metadata.secretSize != share.data.size) {
            return SSSResult.Failure(SSSError.INVALID_SHARE, "Share metadata inconsistent: expected size ${share.metadata.secretSize}, got ${share.data.size}")
        }
        
        return SSSResult.Success(share)
    }
    
    /**
     * Validates a collection of shares for reconstruction.
     */
    fun validateSharesForReconstruction(shares: List<SecretShare>): SSSResult<List<SecretShare>> {
        if (shares.isEmpty()) {
            return SSSResult.Failure(SSSError.INSUFFICIENT_SHARES, "Insufficient shares: no shares provided for reconstruction")
        }
        
        // Validate each share individually
        for (share in shares) {
            when (val result = validateShare(share)) {
                is SSSResult.Failure -> return SSSResult.Failure(result.error, result.message)
                is SSSResult.Success -> { /* Continue */ }
                is SSSResult.PartialReconstruction -> { /* Not expected here */ }
            }
        }
        
        // Check for duplicate indices
        val indices = shares.map { it.index }
        if (indices.size != indices.toSet().size) {
            return SSSResult.Failure(SSSError.INVALID_SHARE, "Duplicate share indices detected")
        }
        
        // Validate all shares come from the same split operation
        val firstShare = shares.first()
        for (i in 1 until shares.size) {
            val share = shares[i]
            
            // Check shareSetId matches
            if (share.metadata.shareSetId != firstShare.metadata.shareSetId) {
                return SSSResult.Failure(
                    SSSError.INCOMPATIBLE_SHARES,
                    "Share set ID mismatch: share ${share.index} has ID '${share.metadata.shareSetId}', " +
                    "expected '${firstShare.metadata.shareSetId}'"
                )
            }
            
            // Check configuration matches
            if (share.metadata.threshold != firstShare.metadata.threshold) {
                return SSSResult.Failure(
                    SSSError.INCOMPATIBLE_SHARES,
                    "Threshold mismatch: share ${share.index} has threshold ${share.metadata.threshold}, " +
                    "expected ${firstShare.metadata.threshold}"
                )
            }
            
            if (share.metadata.totalShares != firstShare.metadata.totalShares) {
                return SSSResult.Failure(
                    SSSError.INCOMPATIBLE_SHARES,
                    "Total shares mismatch: share ${share.index} has total ${share.metadata.totalShares}, " +
                    "expected ${firstShare.metadata.totalShares}"
                )
            }
            
            // Check secret metadata matches
            if (share.metadata.secretSize != firstShare.metadata.secretSize) {
                return SSSResult.Failure(
                    SSSError.INCOMPATIBLE_SHARES,
                    "Secret size mismatch: share ${share.index} has size ${share.metadata.secretSize}, " +
                    "expected ${firstShare.metadata.secretSize}"
                )
            }
            
            if (!share.metadata.secretHash.contentEquals(firstShare.metadata.secretHash)) {
                return SSSResult.Failure(
                    SSSError.INCOMPATIBLE_SHARES,
                    "Secret hash mismatch: shares appear to be from different split operations"
                )
            }
            
            // Check data size consistency
            if (share.data.size != firstShare.data.size) {
                return SSSResult.Failure(
                    SSSError.INCOMPATIBLE_SHARES,
                    "Share data size mismatch: share ${share.index} has size ${share.data.size}, " +
                    "expected ${firstShare.data.size}"
                )
            }
        }
        
        // Check if we have enough shares
        val threshold = firstShare.metadata.threshold
        if (shares.size < threshold) {
            return SSSResult.Failure(
                SSSError.INSUFFICIENT_SHARES,
                "Insufficient shares for reconstruction: have ${shares.size}, need at least $threshold"
            )
        }
        
        return SSSResult.Success(shares)
    }
    
    /**
     * Validates shares against a specific configuration.
     */
    fun validateSharesForConfig(shares: List<SecretShare>, config: SSSConfig): SSSResult<List<SecretShare>> {
        // First validate shares for reconstruction
        val validationResult = validateSharesForReconstruction(shares)
        if (validationResult is SSSResult.Failure) {
            return validationResult
        }
        
        // Then validate against the specific config
        val firstShare = shares.first()
        if (firstShare.metadata.threshold != config.threshold) {
            return SSSResult.Failure(
                SSSError.INCOMPATIBLE_SHARES,
                "Configuration mismatch: shares have threshold ${firstShare.metadata.threshold}, " +
                "expected ${config.threshold}"
            )
        }
        
        if (firstShare.metadata.totalShares != config.totalShares) {
            return SSSResult.Failure(
                SSSError.INCOMPATIBLE_SHARES,
                "Configuration mismatch: shares have total ${firstShare.metadata.totalShares}, " +
                "expected ${config.totalShares}"
            )
        }
        
        return SSSResult.Success(shares)
    }
}