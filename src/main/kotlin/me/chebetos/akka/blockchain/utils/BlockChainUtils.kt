package me.chebetos.akka.blockchain.utils

import me.chebetos.akka.blockchain.model.Block
import java.io.UnsupportedEncodingException
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException


class BlockChainUtils {
    companion object {
        fun calculateHash(data: String): String? {
            return try {
                val digest = MessageDigest.getInstance("SHA-256")
                val rawHash = digest.digest(data.toByteArray(charset("UTF-8")))
                val hexString = StringBuffer()
                for (i in rawHash.indices) {
                    val hex = Integer.toHexString(0xff and rawHash[i].toInt())
                    if (hex.length == 1) hexString.append('0')
                    hexString.append(hex)
                }
                hexString.toString()
            } catch (e: NoSuchAlgorithmException) {
                e.printStackTrace()
                null
            } catch (e: UnsupportedEncodingException) {
                e.printStackTrace()
                null
            }
        }

        fun validateBlock(block: Block): Boolean {
            val dataToEncode: String =
                block.previousHash + block.transaction.timestamp.toString() + block.nonce.toString() + block.transaction
            val checkHash = calculateHash(dataToEncode)
            return checkHash == block.hash
        }
    }
}