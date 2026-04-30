package org.pedrofelix.concurrency.course.coroutinesx

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import kotlin.test.Test
import kotlin.test.assertNotEquals

class JobTests {

    companion object {
        private val logger = LoggerFactory.getLogger(JobTests::class.java)
    }

    @Test
    fun `a launch defines a new scope`() {
        var outerScope: CoroutineScope? = null
        var innerScope: CoroutineScope? = null
        runBlocking {
            outerScope = this
            launch {
                innerScope = this
            }
        }
        assertNotEquals(outerScope, innerScope)
    }

    @Test
    fun `job lifecycle`() {
        runBlocking {
            val job = launch {
                launch {
                    delay(1000)
                }
                launch {
                    delay(500)
                }
                delay(200)
            }
            do {
                logger.info("job: {}, {}, {}", job.isActive, job.isCompleted, job.isCancelled)
                delay(100)
            } while (!job.isCompleted)
        }
    }
}
