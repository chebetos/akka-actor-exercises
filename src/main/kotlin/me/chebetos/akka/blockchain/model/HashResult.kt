package me.chebetos.akka.blockchain.model

data class HashResult(
    val nonce: Int,
    val hash: String,
    val complete: Boolean
)
