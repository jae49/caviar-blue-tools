package cb.core.tools.erasure.models

data class EncodingConfig(
    val dataShards: Int,
    val parityShards: Int,
    val shardSize: Int = 8192
) {
    val totalShards: Int = dataShards + parityShards
    
    init {
        require(dataShards > 0) { "Data shards must be positive" }
        require(parityShards > 0) { "Parity shards must be positive" }
        require(totalShards <= 256) { "Total shards cannot exceed 256" }
        require(shardSize > 0) { "Shard size must be positive" }
    }
}