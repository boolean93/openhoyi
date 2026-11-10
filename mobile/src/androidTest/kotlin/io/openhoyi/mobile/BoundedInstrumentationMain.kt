package io.openhoyi.mobile

import android.os.Handler
import android.os.Looper
import java.util.concurrent.ExecutionException
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/** This fixture must fail promptly even when the continuously rendered Mock window never becomes idle. */
internal object BoundedInstrumentationMain {
    fun <T> run(stage:String,timeoutMs:Long=5_000,action:()->T):T {
        check(Looper.myLooper()!=Looper.getMainLooper()) { "Bounded wait must run on the instrumentation thread" }
        val handler=Handler(Looper.getMainLooper())
        val task=FutureTask<T> {action()}
        check(handler.post(task)) { "$stage could not be posted" }
        try {return task.get(timeoutMs,TimeUnit.MILLISECONDS)}
        catch(failure:ExecutionException){throw requireNotNull(failure.cause)}
        catch(failure:TimeoutException){
            // Remove work that has not started. Running main-thread code is never interrupted;
            // any temporary state it owns is restored by that action's own finally block.
            task.cancel(false);handler.removeCallbacks(task)
            throw AssertionError("$stage timed out after ${timeoutMs}ms",failure)
        }
    }
}
