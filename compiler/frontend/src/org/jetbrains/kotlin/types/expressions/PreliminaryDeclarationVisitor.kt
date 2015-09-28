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

class PreliminaryDeclarationVisitor: AssignedVariablesSearcher() {

    companion object {
        fun visitDeclaration(declaration: JetDeclaration, descriptor: DeclarationDescriptor, trace: BindingTrace) {
            val visitor = PreliminaryDeclarationVisitor()
            declaration.accept(visitor)
            // Store into context?
            trace.record(BindingContext.PRELIMINARY_VISITOR, descriptor, visitor);
        }
    }
}