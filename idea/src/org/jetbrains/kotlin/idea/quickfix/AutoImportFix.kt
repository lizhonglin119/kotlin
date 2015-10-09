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

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.daemon.impl.ShowAutoImportPass
import com.intellij.codeInsight.hint.HintManager
import com.intellij.codeInsight.intention.HighPriorityAction
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.packageDependencies.DependencyValidationManager
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiModificationTracker
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.descriptors.DeclarationDescriptorWithVisibility
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.diagnostics.DiagnosticWithParameters2
import org.jetbrains.kotlin.diagnostics.Errors
import org.jetbrains.kotlin.idea.JetBundle
import org.jetbrains.kotlin.idea.actions.KotlinAddImportAction
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.caches.resolve.getResolutionFacade
import org.jetbrains.kotlin.idea.caches.resolve.getResolveScope
import org.jetbrains.kotlin.idea.codeInsight.DescriptorToSourceUtilsIde
import org.jetbrains.kotlin.idea.core.KotlinIndicesHelper
import org.jetbrains.kotlin.idea.core.getResolutionScope
import org.jetbrains.kotlin.idea.core.isVisible
import org.jetbrains.kotlin.idea.project.ProjectStructureUtil
import org.jetbrains.kotlin.idea.util.CallTypeAndReceiver
import org.jetbrains.kotlin.lexer.JetTokens
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.isImportDirectiveExpression
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.types.expressions.OperatorConventions
import org.jetbrains.kotlin.utils.CachedValueProperty
import java.util.*

/**
 * Check possibility and perform fix for unresolved references.
 */
public class AutoImportFix private constructor(
        expression: JetElement,
        type: AutoImportFix.AutoImportType,
        val diagnostics: Collection<Diagnostic> = emptyList()): JetHintAction<JetElement>(expression), HighPriorityAction {

    constructor(
            expression: JetElement,
            type: AutoImportFix.AutoImportType,
            diagnostic: Diagnostic) : this(expression, type, listOf(diagnostic))

    enum class AutoImportType {
        SIMPLE_NAME,
        ARRAY,
        INVOKE,
        DELEGATE_ACCESSOR,
        COMPONENTS
    }

    private val modificationCountOnCreate = PsiModificationTracker.SERVICE.getInstance(element.getProject()).getModificationCount()

    @Volatile private var anySuggestionFound: Boolean? = null

    private val suggestions: Collection<DeclarationDescriptor> by CachedValueProperty(
            {
                val descriptors = computeSuggestions(element, type, diagnostics)
                anySuggestionFound = !descriptors.isEmpty()
                descriptors
            },
            { PsiModificationTracker.SERVICE.getInstance(element.getProject()).getModificationCount() })

    override fun showHint(editor: Editor): Boolean {
        if (!element.isValid() || isOutdated()) return false

        if (HintManager.getInstance().hasShownHintsThatWillHideByOtherHint(true)) return false

        if (suggestions.isEmpty()) return false

        if (!ApplicationManager.getApplication()!!.isUnitTestMode()) {
            val addImportAction = createAction(element.project, editor)
            val hintText = ShowAutoImportPass.getMessage(suggestions.size() > 1, addImportAction.highestPriorityFqName.asString())
            HintManager.getInstance().showQuestionHint(editor, hintText, element.getTextOffset(), element.getTextRange()!!.getEndOffset(), addImportAction)
        }

        return true
    }

    override fun getText() = JetBundle.message("import.fix")

    override fun getFamilyName() = JetBundle.message("import.fix")

    override fun isAvailable(project: Project, editor: Editor, file: PsiFile)
            = (super.isAvailable(project, editor, file)) && (anySuggestionFound ?: !suggestions.isEmpty())

    override fun invoke(project: Project, editor: Editor?, file: JetFile) {
        CommandProcessor.getInstance().runUndoTransparentAction {
            createAction(project, editor!!).execute()
        }
    }

    override fun startInWriteAction() = true

    private fun isOutdated() = modificationCountOnCreate != PsiModificationTracker.SERVICE.getInstance(element.getProject()).getModificationCount()

    private fun createAction(project: Project, editor: Editor): KotlinAddImportAction {
        if (diagnostics.firstOrNull()?.factory == Errors.COMPONENT_FUNCTION_MISSING) {
            return KotlinAddImportAction(project, editor, element, suggestions, all = true)
        }

        return KotlinAddImportAction(project, editor, element, suggestions)
    }

    companion object : JetSingleIntentionActionFactory() {
        override fun createAction(diagnostic: Diagnostic): JetIntentionAction<JetElement>? {
            // There could be different psi elements (i.e. JetArrayAccessExpression), but we can fix only JetSimpleNameExpression case
            val psiElement = diagnostic.getPsiElement()
            if (psiElement is JetSimpleNameExpression) {
                return AutoImportFix(psiElement, AutoImportType.SIMPLE_NAME, diagnostic)
            }

            if (psiElement is JetArrayAccessExpression) {
                return AutoImportFix(psiElement, AutoImportType.ARRAY, diagnostic)
            }

            val parent = psiElement.parent
            if (parent is JetPropertyDelegate && parent.expression == psiElement) {
                return AutoImportFix(parent, AutoImportType.DELEGATE_ACCESSOR, diagnostic)
            }

            if (psiElement is JetExpression && diagnostic.factory == Errors.COMPONENT_FUNCTION_MISSING) {
                return AutoImportFix(psiElement, AutoImportType.COMPONENTS, diagnostic)
            }

            if (diagnostic.factory == Errors.FUNCTION_EXPECTED && psiElement is JetExpression) {
                return AutoImportFix(psiElement, AutoImportType.INVOKE, diagnostic)
            }

            return null
        }

        override fun canFixSeveralSameProblems(): Boolean = true
        override fun doCreateActions(sameTypeDiagnostics: List<Diagnostic>): List<IntentionAction> {
            val first = sameTypeDiagnostics.first()
            val element = first.psiElement

            if (element !is JetExpression || sameTypeDiagnostics.any { it.factory != Errors.COMPONENT_FUNCTION_MISSING }) {
                return emptyList()
            }

            return listOf(AutoImportFix(element, AutoImportType.COMPONENTS, sameTypeDiagnostics))
        }

        override fun isApplicableForCodeFragment() = true

        private val ERRORS by lazy(LazyThreadSafetyMode.PUBLICATION) { QuickFixes.getInstance().getDiagnostics(this) }

        public fun computeSuggestions(element: JetElement, type: AutoImportType, diagnostics: Collection<Diagnostic>): Collection<DeclarationDescriptor> {
            if (!element.isValid()) return listOf()

            val file = element.getContainingFile() as? JetFile ?: return emptyList()

            val callTypeAndReceiver = when (type) {
                AutoImportType.COMPONENTS -> CallTypeAndReceiver.COMPONENT(element as JetExpression)
                AutoImportType.INVOKE -> CallTypeAndReceiver.DOT(element as JetExpression)
                else -> CallTypeAndReceiver.detect(element)
            }

            if (callTypeAndReceiver is CallTypeAndReceiver.UNKNOWN) return emptyList()

            var referenceName: String = when (type) {
                AutoImportType.SIMPLE_NAME -> {
                    element as JetSimpleNameExpression

                    if (element.getIdentifier() == null) {
                        val conventionName = JetPsiUtil.getConventionName(element)
                        if (conventionName != null) {
                            conventionName.asString()
                        } else {
                            ""
                        }
                    } else {
                        element.getReferencedName()
                    }
                }

                AutoImportType.ARRAY ->
                    if ((element.parent as? JetBinaryExpression)?.operationToken == JetTokens.EQ) "set" else "get"
                AutoImportType.DELEGATE_ACCESSOR ->
                    if (diagnostics.first().toString().contains("set")) "set" else "get"

                AutoImportType.INVOKE -> "invoke"
                AutoImportType.COMPONENTS -> {
                    if (diagnostics.size == 1) {
                        @Suppress("UNCHECKED_CAST")
                        val d = diagnostics.first() as DiagnosticWithParameters2<*, Name, *>
                        val functionName = d.a

                        functionName.identifier
                    }
                    else {
                        return diagnostics.flatMapTo(arrayListOf()) { computeSuggestions(element, type, listOf(it)) }
                    }
                }
                else -> ""
            }
            if (referenceName.isEmpty()) return emptyList()


            val suggestionsForName = computeSuggestionsForName(callTypeAndReceiver, element, file, referenceName)


            if (suggestionsForName.isEmpty()) {
                if (element is JetOperationReferenceExpression) {
                    val elementType = element.firstChild.node.elementType
                    if (OperatorConventions.ASSIGNMENT_OPERATIONS.containsKey(elementType)) {
                        val conterpart = OperatorConventions.ASSIGNMENT_OPERATION_COUNTERPARTS.get(elementType)
                        val counterpartName = OperatorConventions.BINARY_OPERATION_NAMES.get(conterpart)
                        if (counterpartName != null) {
                            return computeSuggestionsForName(callTypeAndReceiver, element, file, counterpartName.toString())
                        }

                    }
                }
            }

            return suggestionsForName
        }

        private fun computeSuggestionsForName(callTypeAndReceiver: CallTypeAndReceiver<out JetElement?, *>, element: JetElement, file: JetFile, referenceName: String): Collection<DeclarationDescriptor> {
            fun filterByCallType(descriptor: DeclarationDescriptor) = callTypeAndReceiver.callType.descriptorKindFilter.accepts(descriptor)

            val searchScope = getResolveScope(file)

            val bindingContext = element.analyze(BodyResolveMode.PARTIAL)

            val diagnostics = bindingContext.getDiagnostics().forElement(when (element) {
                                                                             is JetPropertyDelegate -> element.expression!!
                                                                             else -> element
                                                                         })
            if (!diagnostics.any { it.getFactory() in ERRORS }) return emptyList()

            val resolutionScope = element.getResolutionScope(bindingContext, file.getResolutionFacade())
            val containingDescriptor = resolutionScope.ownerDescriptor

            fun isVisible(descriptor: DeclarationDescriptor): Boolean {
                if (descriptor is DeclarationDescriptorWithVisibility) {
                    return descriptor.isVisible(containingDescriptor, bindingContext, element as? JetSimpleNameExpression)
                }

                return true
            }

            val result = ArrayList<DeclarationDescriptor>()

            val indicesHelper = KotlinIndicesHelper(element.getResolutionFacade(), searchScope, ::isVisible, true)

            if (element is JetSimpleNameExpression) {
                if (!element.isImportDirectiveExpression() && !JetPsiUtil.isSelectorInQualified(element)) {
                    if (ProjectStructureUtil.isJsKotlinModule(file)) {
                        indicesHelper.getKotlinClasses({ it == referenceName }, { true }).filterTo(result, ::filterByCallType)

                    }
                    else {
                        indicesHelper.getJvmClassesByName(referenceName).filterTo(result, ::filterByCallType)
                    }

                    indicesHelper.getTopLevelCallablesByName(referenceName).filterTo(result, ::filterByCallType)
                }
            }

            result.addAll(indicesHelper.getCallableTopLevelExtensions(
                    { it == referenceName }, callTypeAndReceiver,
                    if (element is JetExpression) element else (element as JetPropertyDelegate).expression!!, bindingContext))

            return if (result.size() > 1)
                reduceCandidatesBasedOnDependencyRuleViolation(result, file)
            else
                result
        }

        private fun reduceCandidatesBasedOnDependencyRuleViolation(candidates: Collection<DeclarationDescriptor>, file: PsiFile): Collection<DeclarationDescriptor> {
            val project = file.project
            val validationManager = DependencyValidationManager.getInstance(project)
            return candidates.filter {
                val targetFile = DescriptorToSourceUtilsIde.getAnyDeclaration(project, it)?.containingFile ?: return@filter true
                validationManager.getViolatorDependencyRules(file, targetFile).isEmpty()
            }
        }
    }
}
