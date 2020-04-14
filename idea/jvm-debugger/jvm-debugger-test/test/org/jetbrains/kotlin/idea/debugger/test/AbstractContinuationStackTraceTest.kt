/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.debugger.test

import com.intellij.debugger.engine.AsyncStackTraceProvider
import com.intellij.debugger.engine.JavaStackFrame
import com.intellij.debugger.engine.SuspendContextImpl
import com.intellij.debugger.impl.OutputChecker
import com.intellij.debugger.memory.utils.StackFrameItem
import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.openapi.extensions.Extensions
import com.intellij.xdebugger.frame.*
import org.jetbrains.kotlin.idea.debugger.coroutine.CoroutineAsyncStackTraceProvider
import org.jetbrains.kotlin.idea.debugger.coroutine.util.format
import org.jetbrains.kotlin.idea.debugger.invokeInManagerThread
import org.jetbrains.kotlin.idea.debugger.test.preference.DebuggerPreferences
import org.jetbrains.kotlin.idea.debugger.test.util.KotlinOutputChecker
import org.jetbrains.kotlin.idea.debugger.test.util.XDebuggerTestUtil
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstanceOrNull
import java.io.PrintWriter
import java.io.StringWriter

abstract class AbstractContinuationStackTraceTest : KotlinDescriptorTestCaseWithStackFrames() {
    override fun doMultiFileTest(files: TestFiles, preferences: DebuggerPreferences) {
        val asyncStackTraceProvider = getAsyncStackTraceProvider()

        doWhenXSessionPausedThenResume {
            printContext(debugProcess.debuggerContext)
            val suspendContext = debuggerSession.xDebugSession?.getSuspendContext()
            var executionStack = suspendContext?.getActiveExecutionStack()
            if (executionStack != null) {
                try {
                    out("Thread stack trace:")
                    val stackFrames: List<XStackFrame> = XDebuggerTestUtil.collectFrames(executionStack)
                    for (frame in stackFrames) {
                        if (frame is JavaStackFrame) {
                            out(frame)
                            asyncStackTraceProvider?.getAsyncStackTrace(frame, suspendContext as SuspendContextImpl)?.let {
                                for (frameItem in it)
                                    out(frameItem)
                                return@doWhenXSessionPausedThenResume
                            }
                        }
                    }
                } catch (e: Throwable) {
                    val stackTrace = e.stackTraceAsString()
                    System.err.println("Exception occurred on calculating async stack traces: $stackTrace")
                    throw e
                }
            } else {
                println("FrameProxy is 'null', can't calculate async stack trace", ProcessOutputTypes.SYSTEM)
            }
        }
    }
}
