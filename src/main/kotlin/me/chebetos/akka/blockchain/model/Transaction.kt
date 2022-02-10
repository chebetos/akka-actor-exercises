package me.chebetos.akka.blockchain.model

data class Transaction(
    val id: Int,
    val timestamp: Long,
    val accountNumber: Int,
    val amount: Double
)
