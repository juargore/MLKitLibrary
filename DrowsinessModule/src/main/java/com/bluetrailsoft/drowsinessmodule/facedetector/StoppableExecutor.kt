package com.bluetrailsoft.drowsinessmodule.facedetector

import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean

class StoppableExecutor(): Executor {
    private var executor: Executor? = null
    private val shutdown = AtomicBoolean()

    constructor(executor: Executor) : this() {
        this.executor = executor
    }

    @Suppress("LABEL_NAME_CLASH")
    override fun execute(command: Runnable) {
        if (shutdown.get()) {
            return
        }
        executor!!.execute {

            // Double check in case it has been shut down in the mean time.
            if (shutdown.get()) {
                return@execute
            }
            command.run()
        }
    }

    fun shutdown() {
        shutdown.set(true)
    }
}