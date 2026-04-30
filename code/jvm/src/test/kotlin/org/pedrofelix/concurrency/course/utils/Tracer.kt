package org.pedrofelix.concurrency.course.utils

import org.slf4j.LoggerFactory
import kotlin.test.assertEquals

/**
 * Non-thread safe class to contain and assert trace messages
 */
class Tracer {

    companion object {
        private val logger = LoggerFactory.getLogger(Tracer::class.java)
    }

    private val traces = mutableListOf<String>()

    fun trace(msg: String) {
        logger.info("trace: {}", msg)
        traces.add(msg)
    }

    fun assertTraces(vararg expectedTraces: String) {
        val expectedTracesList = expectedTraces.toList()
        assertEquals(expectedTracesList, traces)
    }
}
