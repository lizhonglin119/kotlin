/*
 * Copyright 2010-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.kotlin.idea.highlighter

import com.intellij.codeInsight.daemon.impl.HighlightRangeExtension
import com.intellij.codeInsight.intention.EmptyIntentionAction
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.lang.annotation.Annotation
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.colors.CodeInsightColors
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.util.TextRange
import com.intellij.psi.MultiRangeReference
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.xml.util.XmlStringUtil
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.diagnostics.Errors
import org.jetbrains.kotlin.diagnostics.Severity
import org.jetbrains.kotlin.diagnostics.rendering.DefaultErrorMessages
import org.jetbrains.kotlin.idea.actions.internal.KotlinInternalMode
import org.jetbrains.kotlin.idea.caches.resolve.analyzeFullyAndGetResult
import org.jetbrains.kotlin.idea.quickfix.JetIntentionActionsFactory
import org.jetbrains.kotlin.idea.quickfix.QuickFixes
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.idea.util.ProjectRootsUtil
import org.jetbrains.kotlin.psi.JetCodeFragment
import org.jetbrains.kotlin.psi.JetFile
import org.jetbrains.kotlin.psi.JetParameter
import org.jetbrains.kotlin.psi.JetReferenceExpression
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.diagnostics.Diagnostics

public open class JetPsiChecker : Annotator, HighlightRangeExtension {

    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        if (!(ProjectRootsUtil.isInProjectOrLibraryContent(element) || element.getContainingFile() is JetCodeFragment)) return

        val file = element.getContainingFile() as JetFile

        val analysisResult = file.analyzeFullyAndGetResult()
        if (analysisResult.isError()) {
            throw ProcessCanceledException(analysisResult.error)
        }

        val bindingContext = analysisResult.bindingContext

        getAfterAnalysisVisitor(holder, bindingContext).forEach { visitor -> element.accept(visitor) }

        annotateElement(element, holder, bindingContext.getDiagnostics())
    }

    override fun isForceHighlightParents(file: PsiFile): Boolean {
        return file is JetFile
    }

    open protected fun shouldSuppressUnusedParameter(parameter: JetParameter): Boolean = false

    fun annotateElement(element: PsiElement, holder: AnnotationHolder, diagnostics: Diagnostics) {
        if (ProjectRootsUtil.isInProjectSource(element) || element.getContainingFile() is JetCodeFragment) {
            ElementAnnotator(element, holder).registerDiagnosticsAnnotations(diagnostics.forElement(element))
        }
    }

    private inner class ElementAnnotator(private val element: PsiElement, private val holder: AnnotationHolder) {
        fun registerDiagnosticsAnnotations(diagnostics: Collection<Diagnostic>) {
            diagnostics.groupBy { diagnostic -> diagnostic.factory }.forEach { group -> registerDiagnosticAnnotations(group.getValue()) }
        }

        private fun registerDiagnosticAnnotations(diagnostics: List<Diagnostic>) {
            assert(diagnostics.isNotEmpty())

            val validDiagnostics = diagnostics.filter { it.isValid }
            if (validDiagnostics.isEmpty()) return

            val diagnostic = diagnostics.first()
            val factory = diagnostic.getFactory()

            assert(diagnostics.all { it.getPsiElement() == element && it.factory == factory })

            val presentationInfo: AnnotationPresentationInfo = when (factory.severity) {
                Severity.ERROR -> {
                    when (factory) {
                        in Errors.UNRESOLVED_REFERENCE_DIAGNOSTICS -> {
                            val referenceExpression = element as JetReferenceExpression
                            val reference = referenceExpression.mainReference
                            if (reference is MultiRangeReference) {
                                AnnotationPresentationInfo(diagnostic,
                                        ranges = diagnostic.textRanges.map { it.shiftRight(referenceExpression.getTextOffset()) },
                                        highlightType = ProblemHighlightType.LIKE_UNKNOWN_SYMBOL)
                            }
                            else {
                                AnnotationPresentationInfo(diagnostic, highlightType = ProblemHighlightType.LIKE_UNKNOWN_SYMBOL)
                            }
                        }

                        Errors.ILLEGAL_ESCAPE -> AnnotationPresentationInfo(diagnostic, textAttributes = JetHighlightingColors.INVALID_STRING_ESCAPE)

                        Errors.REDECLARATION -> AnnotationPresentationInfo(
                                diagnostic, ranges = listOf(diagnostic.getTextRanges().first()), defaultMessage = "")

                        else -> {
                            AnnotationPresentationInfo(diagnostic,
                                    highlightType = if (factory == Errors.INVISIBLE_REFERENCE)
                                        ProblemHighlightType.LIKE_UNKNOWN_SYMBOL
                                    else
                                        null)
                        }
                    }
                }
                Severity.WARNING -> {
                    if (factory == Errors.UNUSED_PARAMETER && shouldSuppressUnusedParameter(element as JetParameter)) {
                        return
                    }

                    AnnotationPresentationInfo(diagnostic,
                            textAttributes = when (factory) {
                                Errors.DEPRECATED_SYMBOL, Errors.DEPRECATED_SYMBOL_WITH_MESSAGE ->
                                    CodeInsightColors.DEPRECATED_ATTRIBUTES
                                else -> null
                            },
                            highlightType = if (factory in Errors.UNUSED_ELEMENT_DIAGNOSTICS)
                                ProblemHighlightType.LIKE_UNUSED_SYMBOL
                            else
                                null
                    )
                }
                Severity.INFO -> return // Do nothing
            }

            setUpAnnotations(diagnostics, presentationInfo)
        }

        private fun setUpAnnotations(diagnostics: List<Diagnostic>, data: AnnotationPresentationInfo) {
            for (range in data.ranges) {
                registerQuickFixes(diagnostics, range, data)
            }
        }

        fun registerQuickFixes(diagnostics: List<Diagnostic>, range: TextRange, data: AnnotationPresentationInfo) {
            val (multiFixes, processedFactories) = createQuickFixesForSameTypeDiagnostics(diagnostics)

            val annotations = diagnostics.map { diagnostic ->
                val annotation = data.create(range, holder)

                createQuickfixes(diagnostic, processedFactories).forEach { annotation.registerFix(it) }

                // Making warnings suppressable
                if (diagnostic.getSeverity() == Severity.WARNING) {
                    annotation.setProblemGroup(KotlinSuppressableWarningProblemGroup(diagnostic.getFactory()))

                    val fixes = annotation.getQuickFixes()
                    if (fixes == null || (fixes.isEmpty() && multiFixes.isEmpty())) {
                        // if there are no quick fixes we need to register an EmptyIntentionAction to enable 'suppress' actions
                        annotation.registerFix(EmptyIntentionAction(diagnostic.getFactory().getName()))
                    }
                }

                annotation
            }

            // Always register all group fixes on the same annotation
            val firstAnnotation = annotations.minBy { it.message }!!
            multiFixes.forEach { fix -> firstAnnotation.registerFix(fix) }
        }

        private fun createQuickFixesForSameTypeDiagnostics(diagnostics: List<Diagnostic>):
                Pair<Collection<IntentionAction>, Collection<JetIntentionActionsFactory>> {
            val factory = diagnostics.first().factory
            val sameProblemsFixesFactories = QuickFixes.getInstance().getActionFactories(factory).filter { it.canFixSeveralSameProblems() }

            val processedFactories = hashSetOf<JetIntentionActionsFactory>()
            val fixActions = arrayListOf<IntentionAction>()

            for (actionFactory in sameProblemsFixesFactories) {
                val actions = actionFactory.createActions(diagnostics)
                if (actions.isNotEmpty()) {
                    processedFactories.add(actionFactory)
                    fixActions.addAll(actions)
                }
            }

            return Pair(fixActions, processedFactories)
        }
    }

    companion object {
        private fun getMessage(diagnostic: Diagnostic): String {
            var message = IdeErrorMessages.render(diagnostic)
            if (KotlinInternalMode.enabled || ApplicationManager.getApplication().isUnitTestMode()) {
                val factoryName = diagnostic.getFactory().getName()
                if (message.startsWith("<html>")) {
                    message = "<html>[$factoryName] ${message.substring("<html>".length())}"
                }
                else {
                    message = "[$factoryName] $message"
                }
            }
            if (!message.startsWith("<html>")) {
                message = "<html><body>${XmlStringUtil.escapeString(message)}</body></html>"
            }
            return message
        }

        private fun getDefaultMessage(diagnostic: Diagnostic): String {
            val message = DefaultErrorMessages.render(diagnostic)
            if (KotlinInternalMode.enabled || ApplicationManager.getApplication().isUnitTestMode()) {
                return "[${diagnostic.getFactory().getName()}] $message"
            }
            return message
        }

        class AnnotationPresentationInfo(
                diagnostic: Diagnostic,
                val severity: Severity = diagnostic.severity,
                val ranges: List<TextRange> = diagnostic.textRanges,
                val defaultMessage: String = getDefaultMessage(diagnostic),
                val tooltip: String = getMessage(diagnostic),
                val highlightType: ProblemHighlightType? = null,
                val textAttributes: TextAttributesKey? = null) {

            public fun create(range: TextRange, holder: AnnotationHolder): Annotation {
                val annotation = when (severity) {
                    Severity.ERROR -> holder.createErrorAnnotation(range, defaultMessage)
                    Severity.WARNING -> holder.createWarningAnnotation(range, defaultMessage)
                    else -> throw IllegalArgumentException("Only ERROR and WARNING diagnostics are supported")
                }

                annotation.tooltip = tooltip

                if (highlightType != null) {
                    annotation.highlightType = highlightType
                }

                if (textAttributes != null) {
                    annotation.textAttributes = textAttributes
                }

                return annotation
            }
        }

        private fun getAfterAnalysisVisitor(holder: AnnotationHolder, bindingContext: BindingContext) = arrayOf(
                PropertiesHighlightingVisitor(holder, bindingContext),
                FunctionsHighlightingVisitor(holder, bindingContext),
                VariablesHighlightingVisitor(holder, bindingContext),
                TypeKindHighlightingVisitor(holder, bindingContext)
        )

        public fun createQuickfixes(
                diagnostic: Diagnostic,
                excludedFactories : Collection<JetIntentionActionsFactory> = emptySet<JetIntentionActionsFactory>()): Collection<IntentionAction> {
            val result = arrayListOf<IntentionAction>()
            val intentionActionsFactories = QuickFixes.getInstance().getActionFactories(diagnostic.getFactory()) - excludedFactories
            for (intentionActionsFactory in intentionActionsFactories.filterNotNull()) {
                result.addAll(intentionActionsFactory.createActions(diagnostic))
            }
            result.addAll(QuickFixes.getInstance().getActions(diagnostic.getFactory()))
            return result
        }
    }
}
