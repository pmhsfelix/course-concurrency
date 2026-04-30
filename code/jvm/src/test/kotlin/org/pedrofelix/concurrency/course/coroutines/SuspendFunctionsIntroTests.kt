package org.pedrofelix.concurrency.course.coroutines

import org.slf4j.LoggerFactory
import java.util.ArrayDeque
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.createCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.test.Test
import kotlin.test.assertNotNull

class SuspendFunctionsIntroTests {

    companion object {
        private val logger = LoggerFactory.getLogger(SuspendFunctionsIntroTests::class.java)
    }

    /**
     * A suspend function that doesn't suspend at all.
     */
    suspend fun sf0(
        x: Int,
        y: Int,
    ): String {
        logger.info("sf0 started.")
        return (x + y).toString()
    }

    @Test
    fun `cannot call a suspend function directly from a non-suspend function`() {
        // sf0(2,3)
        // Compilation error:
        // Suspend function 'suspend fun sf0(x: Int, y: Int): Int'
        // can only be called from a coroutine or another suspend function.
    }

    @Test
    fun `suspend functions use the continuation passing style (CPS) - 0`() {
        @Suppress("UNCHECKED_CAST")
        val nonSuspendVersion = ::sf0 as (Int, Int, Continuation<String>) -> Any
        val continuation = object : Continuation<String> {
            override val context = EmptyCoroutineContext

            override fun resumeWith(result: Result<String>) {
                logger.info("resumeWith: $result.")
            }
        }
        val res = nonSuspendVersion(2, 3, continuation)
        logger.info("res: $res.")
    }

    var sf1ResumeContinuation: Continuation<Int>? = null

    suspend fun sf1(
        x: Int,
        y: Int,
    ): String {
        logger.info("sf1 started.")
        val z = suspendCoroutine { cont ->
            sf1ResumeContinuation = cont
        }
        logger.info("sf1, after suspendCoroutine.")
        return (x + y + z).toString()
    }

    @Test
    fun `suspend functions use the continuation passing style (CPS) - 1`() {
        @Suppress("UNCHECKED_CAST")
        val nonSuspendVersion = ::sf1 as (Int, Int, Continuation<String>) -> Any
        val continuation = object : Continuation<String> {
            override val context = EmptyCoroutineContext

            override fun resumeWith(result: Result<String>) {
                logger.info("resumeWith: $result.")
            }
        }
        val res = nonSuspendVersion(2, 3, continuation)
        logger.info("res: $res")
        assertNotNull(sf1ResumeContinuation)
        sf1ResumeContinuation?.resume(5)
    }

    val continuations = ArrayDeque<Continuation<Unit>>()

    suspend fun sf2(): String {
        logger.info("sf2 started")
        repeat(2) {
            logger.info("sf2, before suspend on iteration $it.")
            suspendCoroutine { cont ->
                continuations.addLast(cont)
            }
            logger.info("sf2, after suspend on iteration $it.")
        }
        logger.info("sf2 ending")
        return "sf2 result"
    }

    suspend fun sf3(): String {
        logger.info("sf3 started")
        repeat(3) {
            logger.info("sf3, before suspend on iteration $it.")
            suspendCoroutine { cont ->
                continuations.addLast(cont)
            }
            logger.info("sf3, after suspend on iteration $it.")
        }
        logger.info("sf3 ending")
        return "sf2 result"
    }

    @Test
    fun `time multiplexing between two suspend function - 0`() {
        // Get the Direct Style (DS) views on sf2 and sf3
        val sf2ds = ::sf2 as (Continuation<String>) -> Any
        val sf3ds = ::sf3 as (Continuation<String>) -> Any
        val continuation = object : Continuation<String> {
            override val context = EmptyCoroutineContext

            override fun resumeWith(result: Result<String>) {
                logger.info("resumeWith: $result.")
            }
        }
        // start both function
        sf2ds(continuation)
        sf3ds(continuation)
        // start both function
        while (continuations.isNotEmpty()) {
            logger.info("Scheduler, resuming continuation.")
            continuations.removeFirst().resume(Unit)
            logger.info("Scheduler, continuation returned.")
        }
        logger.info("Scheduler, ended.")
    }

    @Test
    fun `time multiplexing between two suspend function - 1 - using createCoroutine`() {
        val continuation = object : Continuation<String> {
            override val context = EmptyCoroutineContext

            override fun resumeWith(result: Result<String>) {
                logger.info("resumeWith: $result.")
            }
        }
        val continuationToTheStartOfSf2 = ::sf2.createCoroutine(continuation)
        val continuationToTheStartOfSf3 = ::sf3.createCoroutine(continuation)
        continuations.addLast(continuationToTheStartOfSf2)
        continuations.addLast(continuationToTheStartOfSf3)

        logger.info("Scheduler, starting.")
        while (continuations.isNotEmpty()) {
            logger.info("Scheduler, resuming continuation.")
            continuations.removeFirst().resume(Unit)
            logger.info("Scheduler, continuation returned.")
        }
        logger.info("Scheduler, ended.")
    }
}
