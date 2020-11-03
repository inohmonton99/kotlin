/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.runInEdtAndGet
import org.jetbrains.kotlin.idea.codeInsight.gradle.MasterPluginVersionGradleImportingTestCase
import org.jetbrains.kotlin.idea.codeInsight.gradle.mppImportTestMinVersionForMaster
import org.jetbrains.kotlin.idea.core.util.toPsiFile
import org.jetbrains.kotlin.idea.test.GradleProcessOutputInterceptor
import org.jetbrains.kotlin.idea.util.application.runReadAction
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid
import org.jetbrains.kotlin.psi.psiUtil.startOffset
import org.jetbrains.plugins.gradle.tooling.annotation.PluginTargetVersions
import org.junit.Test

class ImportAndCheckNavigation : MasterPluginVersionGradleImportingTestCase() {

    override fun setUp() {
        super.setUp()
        GradleProcessOutputInterceptor.install(testRootDisposable)
    }

    @Test
    @PluginTargetVersions(gradleVersion = "6.0+", pluginVersion = "1.4+", gradleVersionForLatestPlugin = mppImportTestMinVersionForMaster)
    fun testNavigationToCommonizedLibrary() {
        val files = importProjectFromTestData()

        files.forEach { vFile ->
            val referencesToTest = vFile.collectReferencesToTest()
            referencesToTest.forEach { (psiReference, expectedElementText) ->
                runReadAction {
                    val referencedElement = psiReference.resolve()
                    assertNotNull(
                        "PSI reference \"${psiReference.canonicalText}\" in ${vFile.relPath} can't be resolved",
                        referencedElement
                    )

                    val referencedElementText = referencedElement!!.text
                    assertTrue(
                        "Resolved reference \"${psiReference.canonicalText}\" from ${vFile.relPath} does not " +
                                "contain \"$expectedElementText\", instead it contains \"$referencedElementText\"",
                        expectedElementText in referencedElementText
                    )
                }
            }
        }
    }

    override fun testDataDirName() = "importAndCheckNavigation"

    override fun handleImportFailure(errorMessage: String, errorDetails: String?) {
        val gradleOutput = GradleProcessOutputInterceptor.getInstance()?.getOutput().orEmpty()
        val compactErrorMessage = when (val indexOfNewLine = errorMessage.indexOf('\n')) {
            -1 -> errorMessage
            else -> {
                val compactErrorMessage = errorMessage.substring(0, indexOfNewLine)
                val theRest = errorMessage.substring(indexOfNewLine + 1)
                if (theRest in gradleOutput) compactErrorMessage else errorMessage
            }
        }

        val failureMessage = buildString {
            append("Gradle import failed: ").append(compactErrorMessage).append('\n')
            if (!errorDetails.isNullOrBlank())
                append("Error details: ").append(errorDetails).append('\n')
            append("Gradle process output:\n")
            append(gradleOutput)
        }
        fail(failureMessage)
    }

    private fun VirtualFile.collectReferencesToTest(): Map<PsiReference, String> {
        if (extension != "kt") return emptyMap()

        val referencesToTest = mutableMapOf<PsiReference, String>()

        runInEdtAndGet {
            val psiFile = toPsiFile(project)
            assertNotNull(
                "Can't get PSI file for $relPath",
                psiFile
            )

            psiFile!!.accept(object : KtTreeVisitorVoid() {
                override fun visitComment(comment: PsiComment) {
                    val commentText = comment.text ?: return

                    val unwrappedCommentText = if (commentText.startsWith("/*")) {
                        commentText.removePrefix("/*").removeSuffix("*/")
                    } else {
                        commentText.removePrefix("//")
                    }.trim(Char::isWhitespace)

                    if (unwrappedCommentText.startsWith("NAVIGATION-TARGET:")) {
                        val expectedElementText = unwrappedCommentText.substringAfter("NAVIGATION-TARGET:").trimStart(Char::isWhitespace)
                        assertTrue(
                            "Empty expected element text in $relPath in comment \"$commentText\" at offset ${comment.startOffset}",
                            expectedElementText.isNotEmpty()
                        )

                        val nextElement = PsiTreeUtil.skipSiblingsForward(
                            comment,
                            PsiWhiteSpace::class.java, PsiComment::class.java
                        )
                        assertNotNull(
                            "Next element not found in $relPath after comment \"$commentText\" at offset ${comment.startOffset}",
                            nextElement
                        )

                        val reference = nextElement!!.findReferenceAt(0)
                        assertNotNull(
                            "Can find PSI reference for \"${nextElement.text}\" in $relPath at offset ${nextElement.startOffset}",
                            reference
                        )

                        referencesToTest[reference!!] = expectedElementText
                    }
                }
            })

        }

        return referencesToTest
    }

    private val VirtualFile.relPath: String
        get() = canonicalPath?.removePrefix(projectPath)?.trimStart('/', '\\') ?: name
}
