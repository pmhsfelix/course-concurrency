package org.pedrofelix.concurrency.course.costs

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Semaphore
import kotlin.test.Test

class ThreadCostTests {

    companion object {
        const val N_OF_THREADS = 1_000
        const val N_OF_ITERATIONS = 100
    }

    private fun creatingAndContextSwitchingThreads(threadBuild: Thread.Builder) {
        val semaphore = Semaphore(0, true)
        val latch = CountDownLatch(N_OF_THREADS)
        val threads = List(N_OF_THREADS) {
            threadBuild.start {
                latch.countDown()
                repeat(N_OF_ITERATIONS) {
                    semaphore.acquire(1)
                    semaphore.release(1)
                }
            }
        }
        latch.await()
        semaphore.release(1)
        threads.forEach { it.join() }
    }

    @Test
    fun `with platform threads`() {
        creatingAndContextSwitchingThreads(Thread.ofPlatform())
    }

    @Test
    fun `with virtual threads`() {
        creatingAndContextSwitchingThreads(Thread.ofVirtual())
    }
}
