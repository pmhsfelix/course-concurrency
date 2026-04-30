package org.pedrofelix.concurrency.course.coroutines

import org.pedrofelix.concurrency.course.utils.Tracer
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.Instant
import java.util.concurrent.DelayQueue
import java.util.concurrent.Delayed
import java.util.concurrent.TimeUnit
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.createCoroutine
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import kotlin.coroutines.intrinsics.suspendCoroutineUninterceptedOrReturn
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.test.Test

private val logger = LoggerFactory.getLogger(SuspendTests::class.java)

class SuspendTests {

    private val tracer = Tracer()
    private val terminalContinuation = object : Continuation<Int> {
        override val context = EmptyCoroutineContext

        override fun resumeWith(result: Result<Int>) {
            tracer.trace("Terminal continuation called with result=$result.")
        }
    }

    @Test
    fun `understanding suspend functions`() {
        var currentContinuation: Continuation<Unit>? = null

        /*
         A suspend function with a loop. On each loop iteration, the suspend function
         suspend it execution, effectively returning to the caller. During that process
         a reference to the continuation point after the resume point is provided and
         this example code stores it on a variable.
         */
        suspend fun aSuspendFunction(s: String): Int {
            tracer.trace("[inside] Suspend function called with s=$s.")
            repeat(3) {
                tracer.trace("[inside] On iteration $it, before suspend.")

                // suspendCoroutineUninterceptedOrReturn is an intrinsic function
                // (i.e. a function implemented directly by the compiler)
                // that calls the provided block with a reference to the next statement.
                // This reference is called the continuation and in this case points to the
                // logger.trace(...) statement.
                suspendCoroutineUninterceptedOrReturn { continuation ->
                    // here we just store the provided continuation on an external variable.
                    currentContinuation = continuation
                    // this tell the suspendCoroutineUninterceptedOrReturn that we want to suspend
                    COROUTINE_SUSPENDED
                }

                // the continuation will "point" to this statement
                tracer.trace("[inside] On iteration $it, after suspend.")
            }
            tracer.trace("[inside] Returning 42.")
            return 42
        }

        // A suspend function cannot be called directly from a non suspend function, because suspend
        // functions are compiled according to the Continuation Passing Style (CPS), while non suspend
        // functions are compiled in the Direct Style.
        // - In CPS, a function receives an extra argument with a reference to the code that must be run when the
        //   function completes.
        // - This enables the function to have suspension points, where it returns to the immediate caller even
        //   without having completed (e.g. in the middle of a loop)
        // - When it is finally completed, it calls the passed in continuation.
        // Here we just cast the suspend function to a non-suspend function taking into consideration that extra
        // parameter. Since in the function produces an Int, that means that the passed in continuation receives
        // an Int.
        @Suppress("UNCHECKED_CAST")
        val nonSuspendFunctionReference = ::aSuspendFunction as (String, Continuation<Int>) -> Unit

        // This is the continuation we are going to pass to the suspend function and that will be called by it
        // when it completes
        val continuation =
            object : Continuation<Int> {
                override val context = EmptyCoroutineContext

                override fun resumeWith(result: Result<Int>) {
                    tracer.trace("[outside] Terminal continuation called with result=$result.")
                }
            }

        // Let's start by calling the suspend function
        nonSuspendFunctionReference.invoke("start", continuation)
        // The function returns not because it was completed, but because the first suspend point was reached
        logger.trace("[outside] Suspend function returned.")
        while (true) {
            val continuation: Continuation<Unit> = currentContinuation
                ?: break
            currentContinuation = null
            // And we have a continuation that we can call to resume the suspend function.
            tracer.trace("[outside] Calling continuation.")
            continuation.resumeWith(Result.success(Unit))
            // The continuation call returned because a new suspend point was reached
            // or because it completed, called the initially provided continuation and this one also
            // returned.
            tracer.trace("[outside] Continuation returned.")
        }

        // It's a good idea to run this test function and observe the trace messages
        tracer.assertTraces(
            "[inside] Suspend function called with s=start.",
            "[inside] On iteration 0, before suspend.",
            "[outside] Calling continuation.",
            "[inside] On iteration 0, after suspend.",
            "[inside] On iteration 1, before suspend.",
            "[outside] Continuation returned.",
            "[outside] Calling continuation.",
            "[inside] On iteration 1, after suspend.",
            "[inside] On iteration 2, before suspend.",
            "[outside] Continuation returned.",
            "[outside] Calling continuation.",
            "[inside] On iteration 2, after suspend.",
            "[inside] Returning 42.",
            "[outside] Terminal continuation called with result=Success(42).",
            "[outside] Continuation returned.",
        )
    }

    @Test
    fun `time multiplexing two suspend functions on the same main thread`() {
        val continuations = mutableListOf<Continuation<Unit>>()

        // A helper function to suspend a coroutine
        suspend fun suspend() {
            suspendCoroutine { cont ->
                // adds the continuation to the end of the continuations queue
                continuations.addLast(cont)
                // and then suspends
            }
        }

        // The first function
        suspend fun f1(): Int {
            tracer.trace("[f1] Started.")
            repeat(2) {
                tracer.trace("[f1] iteration $it, before suspend.")
                suspend()
                tracer.trace("[f1] iteration $it, after suspend.")
            }
            return 1
        }

        // The second function
        suspend fun f2(): Int {
            tracer.trace("[f2] Started.")
            repeat(3) {
                tracer.trace("[f2] iteration $it, before suspend.")
                suspend()
                tracer.trace("[f2] iteration $it, after suspend.")
            }
            return 2
        }

        // obtain continuations to the start of the functions and add them to the continuations queue
        val continuationToTheStartOff1 = ::f1.createCoroutine(terminalContinuation)
        val continuationToTheStartOff2 = ::f2.createCoroutine(terminalContinuation)
        continuations.addLast(continuationToTheStartOff1)
        continuations.addLast(continuationToTheStartOff2)

        // scheduler
        tracer.trace("[scheduler] Starting.")
        while (continuations.isNotEmpty()) {
            val continuation = continuations.removeFirst()
            tracer.trace("[scheduler] Calling continuation.")
            continuation.resume(Unit)
            tracer.trace("[scheduler] Getting next continuation.")
        }
        tracer.trace("[scheduler] Ended.")

        tracer.assertTraces(
            "[scheduler] Starting.",
            "[scheduler] Calling continuation.",
            "[f1] Started.",
            "[f1] iteration 0, before suspend.",
            "[scheduler] Getting next continuation.",
            "[scheduler] Calling continuation.",
            "[f2] Started.",
            "[f2] iteration 0, before suspend.",
            "[scheduler] Getting next continuation.",
            "[scheduler] Calling continuation.",
            "[f1] iteration 0, after suspend.",
            "[f1] iteration 1, before suspend.",
            "[scheduler] Getting next continuation.",
            "[scheduler] Calling continuation.",
            "[f2] iteration 0, after suspend.",
            "[f2] iteration 1, before suspend.",
            "[scheduler] Getting next continuation.",
            "[scheduler] Calling continuation.",
            "[f1] iteration 1, after suspend.",
            "Terminal continuation called with result=Success(1).",
            "[scheduler] Getting next continuation.",
            "[scheduler] Calling continuation.",
            "[f2] iteration 1, after suspend.",
            "[f2] iteration 2, before suspend.",
            "[scheduler] Getting next continuation.",
            "[scheduler] Calling continuation.",
            "[f2] iteration 2, after suspend.",
            "Terminal continuation called with result=Success(2).",
            "[scheduler] Getting next continuation.",
            "[scheduler] Ended.",
        )
    }

    @Test
    fun `time multiplexing two suspend functions on the same main thread with delays`() {
        class DelayedContinuation<T>(
            val continuation: Continuation<T>,
            val notBefore: Instant,
        ) : Delayed {
            override fun getDelay(unit: TimeUnit): Long = unit.convert(Duration.between(Instant.now(), notBefore))

            override fun compareTo(other: Delayed): Int {
                val otherDelayed = other as DelayedContinuation<*>
                return notBefore.compareTo(otherDelayed.notBefore)
            }
        }

        val continuations = DelayQueue<DelayedContinuation<Unit>>()

        // A helper function to suspend a coroutine
        suspend fun delay(duration: Duration) {
            suspendCoroutine { cont ->
                // adds the continuation to the end of the continuations queue
                continuations.add(
                    DelayedContinuation(
                        cont,
                        Instant.now().plus(duration),
                    ),
                )
                // and then suspends
            }
        }

        // The first function
        suspend fun f1(): Int {
            tracer.trace("[f1] Started.")
            repeat(2) {
                tracer.trace("[f1] iteration $it, before suspend.")
                delay(Duration.ofMillis(2500))
                tracer.trace("[f1] iteration $it, after suspend.")
            }
            return 1
        }

        // The second function
        suspend fun f2(): Int {
            tracer.trace("[f2] Started.")
            repeat(3) {
                tracer.trace("[f2] iteration $it, before suspend.")
                delay(Duration.ofMillis(1000))
                tracer.trace("[f2] iteration $it, after suspend.")
            }
            return 2
        }

        // obtain continuations to the start of the functions and add them to the continuations queue
        val continuationToTheStartOff1 = ::f1.createCoroutine(terminalContinuation)
        val continuationToTheStartOff2 = ::f2.createCoroutine(terminalContinuation)
        continuations.add(
            DelayedContinuation(
                continuationToTheStartOff1,
                Instant.now(),
            ),
        )
        continuations.add(
            DelayedContinuation(
                continuationToTheStartOff2,
                Instant.now(),
            ),
        )

        // scheduler
        tracer.trace("[scheduler] Starting.")
        while (continuations.isNotEmpty()) {
            val delayElement: DelayedContinuation<Unit> = continuations.take()
            tracer.trace("[scheduler] Calling continuation.")
            delayElement.continuation.resume(Unit)
            tracer.trace("[scheduler] Getting next continuation.")
        }
        tracer.trace("[scheduler] Ended.")

        tracer.assertTraces(
            "[scheduler] Starting.",
            "[scheduler] Calling continuation.",
            "[f1] Started.",
            "[f1] iteration 0, before suspend.",
            "[scheduler] Getting next continuation.",
            "[scheduler] Calling continuation.",
            "[f2] Started.",
            "[f2] iteration 0, before suspend.",
            "[scheduler] Getting next continuation.",
            "[scheduler] Calling continuation.",
            "[f2] iteration 0, after suspend.",
            "[f2] iteration 1, before suspend.",
            "[scheduler] Getting next continuation.",
            "[scheduler] Calling continuation.",
            "[f2] iteration 1, after suspend.",
            "[f2] iteration 2, before suspend.",
            "[scheduler] Getting next continuation.",
            "[scheduler] Calling continuation.",
            "[f1] iteration 0, after suspend.",
            "[f1] iteration 1, before suspend.",
            "[scheduler] Getting next continuation.",
            "[scheduler] Calling continuation.",
            "[f2] iteration 2, after suspend.",
            "Terminal continuation called with result=Success(2).",
            "[scheduler] Getting next continuation.",
            "[scheduler] Calling continuation.",
            "[f1] iteration 1, after suspend.",
            "Terminal continuation called with result=Success(1).",
            "[scheduler] Getting next continuation.",
            "[scheduler] Ended.",
        )
    }
}
