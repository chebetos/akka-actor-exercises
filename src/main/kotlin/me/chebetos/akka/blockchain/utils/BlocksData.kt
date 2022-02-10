package me.chebetos.akka.blockchain.utils

import me.chebetos.akka.blockchain.model.PartialBlock
import me.chebetos.akka.blockchain.model.Transaction
import java.util.GregorianCalendar


class BlocksData {
    companion object {
        private val timeStamps = longArrayOf(
            GregorianCalendar(2015, 5, 22, 14, 21).timeInMillis,
            GregorianCalendar(2015, 5, 22, 14, 27).timeInMillis,
            GregorianCalendar(2015, 5, 22, 14, 29).timeInMillis,
            GregorianCalendar(2015, 5, 22, 14, 33).timeInMillis,
            GregorianCalendar(2015, 5, 22, 14, 38).timeInMillis,
            GregorianCalendar(2015, 5, 22, 14, 41).timeInMillis,
            GregorianCalendar(2015, 5, 22, 14, 46).timeInMillis,
            GregorianCalendar(2015, 5, 22, 14, 47).timeInMillis,
            GregorianCalendar(2015, 5, 22, 14, 51).timeInMillis,
            GregorianCalendar(2015, 5, 22, 14, 55).timeInMillis
        )

        private val customerIds = intArrayOf(1732, 1650, 2209, 4545, 324, 1944, 6565, 1805, 1765, 7001)
        private val amounts = doubleArrayOf(103.27, 66.54, -21.09, 44.65, 177.99, 189.02, 17.00, 32.99, 60.00, -10.00)

        fun getNextBlock(id: Int, lastHash: String): PartialBlock {
            val transaction = Transaction(id, timeStamps[id], customerIds[id], amounts[id])
            return PartialBlock(transaction, lastHash)
        }
    }
}