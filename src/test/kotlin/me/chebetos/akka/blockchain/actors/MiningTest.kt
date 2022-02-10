package me.chebetos.akka.blockchain.actors

import akka.actor.testkit.typed.javadsl.BehaviorTestKit
import akka.actor.testkit.typed.javadsl.TestInbox
import me.chebetos.akka.blockchain.model.HashResult
import me.chebetos.akka.blockchain.utils.BlocksData
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.event.Level
import kotlin.test.DefaultAsserter.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class MiningTest {

    @BeforeEach
    fun setUp() {
    }

    @AfterEach
    fun tearDown() {
    }

    @Test
    fun testFailsIfNonceNotInRange() {
        val testActor = BehaviorTestKit.create(WorkerBehavior.create())
        val block = BlocksData.getNextBlock(0, "0")
        val testInbox = TestInbox.create<HashResult>()
        val command = WorkerBehavior.Command(
            block = block, startNonce = 0, difficultyLevel = 5, controller = testInbox.ref
        )

        testActor.run(command)
        val logMessages = testActor.allLogEntries
        assertEquals("log message size doesn't match",1, logMessages.size)
        assertEquals("message doesn't match","null", logMessages[0].message())
        assertEquals("message level doesn't match", Level.INFO, logMessages[0].level())
        assertFalse("testInbox has messages") { testInbox.hasMessages() }
    }

    @Test
    fun testMiningPassesIfNonceIsInRange() {
        val testActor = BehaviorTestKit.create(WorkerBehavior.create())
        val block = BlocksData.getNextBlock(0, "0")
        val testInbox = TestInbox.create<HashResult>()

        val command = WorkerBehavior.Command(
            block = block, startNonce = 935000, difficultyLevel = 5, controller = testInbox.ref
        )

        testActor.run(command)
        val logMessages = testActor.allLogEntries
        assertEquals("log message size doesn't match",1, logMessages.size)
        assertEquals("message doesn't match","935430 : 0000060843488457a1f5993faa72ae031a0bc714cec6c4bcd585912f819e7fa4", logMessages[0].message())
        assertEquals("message level doesn't match", Level.INFO, logMessages[0].level())

        assertTrue("testInbox hasn't messages") { testInbox.hasMessages() }

        val expectedHashResult = HashResult(
            nonce = 935430,
            hash = "0000060843488457a1f5993faa72ae031a0bc714cec6c4bcd585912f819e7fa4",
            complete = true
        )

        testInbox.expectMessage(expectedHashResult)
    }
}