package me.chebetos.akka.blockchain.model

data class PartialBlock(
    val transaction: Transaction,
    val previousHash: String,
)
