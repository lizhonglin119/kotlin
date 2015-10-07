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

package org.jetbrains.kotlin.types.expressions

import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.descriptors.VariableDescriptor
import org.jetbrains.kotlin.psi.JetDeclaration
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.BindingTrace

class PreliminaryDeclarationVisitor(val declaration: JetDeclaration): AssignedVariablesSearcher() {

    override fun writers(variableDescriptor: VariableDescriptor) =
            if (lazyTrigger) super.writers(variableDescriptor)
            else throw AssertionError("Preliminary declaration visitor not initialized")

    private val lazyTrigger by lazy {
        declaration.accept(this)
        true
    }

    companion object {
        fun visitDeclaration(declaration: JetDeclaration, descriptor: DeclarationDescriptor, trace: BindingTrace) {
            val visitor = PreliminaryDeclarationVisitor(declaration)
            trace.record(BindingContext.PRELIMINARY_VISITOR, descriptor, visitor);
        }

        fun forVariable(variableDescriptor: VariableDescriptor, bindingContext: BindingContext): PreliminaryDeclarationVisitor? {
            // Search for preliminary visitor of parent descriptor
            var parentDescriptor: DeclarationDescriptor? = variableDescriptor.containingDeclaration
            var preliminaryVisitor = bindingContext.get(BindingContext.PRELIMINARY_VISITOR, parentDescriptor)
            while (preliminaryVisitor == null && parentDescriptor != null) {
                parentDescriptor = parentDescriptor.containingDeclaration
                preliminaryVisitor = bindingContext.get(BindingContext.PRELIMINARY_VISITOR, parentDescriptor)
            }
            return preliminaryVisitor
        }
    }
}