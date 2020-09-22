/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.asJava

import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiElement
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable
import org.jetbrains.kotlin.descriptors.annotations.AnnotationUseSiteTarget
import org.jetbrains.kotlin.fir.FirSymbolOwner
import org.jetbrains.kotlin.fir.declarations.FirAnnotatedDeclaration
import org.jetbrains.kotlin.fir.declarations.FirResolvePhase
import org.jetbrains.kotlin.fir.expressions.*
import org.jetbrains.kotlin.fir.references.FirResolvedNamedReference
import org.jetbrains.kotlin.fir.symbols.impl.FirPropertySymbol
import org.jetbrains.kotlin.idea.frontend.api.fir.symbols.KtFirAnnotationCall
import org.jetbrains.kotlin.idea.frontend.api.fir.symbols.KtFirSymbol
import org.jetbrains.kotlin.idea.frontend.api.symbols.markers.KtAnnotatedSymbol
import org.jetbrains.kotlin.idea.frontend.api.symbols.markers.KtSimpleConstantValue

internal fun KtAnnotatedSymbol.hasJvmSyntheticAnnotation(annotationUseSiteTarget: AnnotationUseSiteTarget?): Boolean =
    hasAnnotation("kotlin/jvm/JvmSynthetic", annotationUseSiteTarget)

internal fun KtAnnotatedSymbol.getJvmNameFromAnnotation(annotationUseSiteTarget: AnnotationUseSiteTarget? = null): String? {
    val annotation = annotations.firstOrNull {
        val siteTarget = it.useSiteTarget
        (siteTarget == null || siteTarget == annotationUseSiteTarget) &&
                it.classId?.asString() == "kotlin/jvm/JvmName"
    }

    return annotation?.let {
        (it.arguments.firstOrNull()?.expression as? KtSimpleConstantValue<*>)?.constant as? String
    }
}

internal fun KtAnnotatedSymbol.isHiddenByDeprecation(annotationUseSiteTarget: AnnotationUseSiteTarget?): Boolean {

    //TODO Move it to HL API
    require(this is KtFirSymbol<*>)

    return this.firRef.withFir(FirResolvePhase.TYPES) {
        if (it !is FirAnnotatedDeclaration) return@withFir false

        val deprecatedAnnotationCall = it.annotations.firstOrNull { annotationCall ->
            val siteTarget = annotationCall.useSiteTarget
            (siteTarget == null || siteTarget == annotationUseSiteTarget) &&
                    annotationCall.classId?.asString() == "kotlin/Deprecated"
        } ?: return@withFir false

        val deprecatedCallArguments = deprecatedAnnotationCall.argumentMapping ?: return@withFir false

        deprecatedCallArguments.any { (expression, parameter) ->

            if (parameter.name.asString() != "level") return@any false

            val qualifiedExpression = ((expression as? FirNamedArgumentExpression)?.expression as? FirQualifiedAccessExpression)
                ?: return@any false

            val receiverQualifier = (qualifiedExpression.explicitReceiver as? FirResolvedQualifier)?.classId?.asString()
            if (receiverQualifier != "kotlin/DeprecationLevel") return@any false

            val calleeReference = (qualifiedExpression.calleeReference as? FirResolvedNamedReference)?.name?.asString()

            calleeReference == "HIDDEN"


        }

    }
}


internal fun KtAnnotatedSymbol.hasJvmFieldAnnotation(): Boolean =
    hasAnnotation("kotlin/jvm/JvmField", null)

internal fun KtAnnotatedSymbol.hasJvmOverloadsAnnotation(): Boolean =
    hasAnnotation("kotlin/jvm/JvmOverloads", null)

internal fun KtAnnotatedSymbol.hasJvmStaticAnnotation(): Boolean =
    hasAnnotation("kotlin/jvm/JvmStatic", null)

internal fun KtAnnotatedSymbol.hasInlineOnlyAnnotation(): Boolean =
    hasAnnotation("kotlin/internal/InlineOnly", null)

internal fun KtAnnotatedSymbol.hasAnnotation(classIdString: String, annotationUseSiteTarget: AnnotationUseSiteTarget?): Boolean =
    annotations.any {
        val siteTarget = it.useSiteTarget
        (siteTarget == null || siteTarget == annotationUseSiteTarget) && it.classId?.asString() == classIdString
    }

internal fun KtAnnotatedSymbol.computeAnnotations(
    parent: PsiElement,
    nullability: NullabilityType,
    annotationUseSiteTarget: AnnotationUseSiteTarget?
): List<PsiAnnotation> {

    if (nullability == NullabilityType.Unknown && annotations.isEmpty()) return emptyList()

    val nullabilityAnnotation = when (nullability) {
        NullabilityType.NotNull -> NotNull::class.java
        NullabilityType.Nullable -> Nullable::class.java
        else -> null
    }?.let {
        FirLightSimpleAnnotation(it.name, parent)
    }

    if (annotations.isEmpty()) {
        return if (nullabilityAnnotation != null) listOf(nullabilityAnnotation) else emptyList()
    }

    val result = mutableListOf<PsiAnnotation>()
    for (annotation in annotations) {

        val siteTarget = annotation.useSiteTarget
        if (siteTarget == null || siteTarget == annotationUseSiteTarget) {
            result.add(FirLightAnnotationForAnnotationCall(annotation, parent))
        }
    }

    if (nullabilityAnnotation != null) {
        result.add(nullabilityAnnotation)
    }

    return result
}