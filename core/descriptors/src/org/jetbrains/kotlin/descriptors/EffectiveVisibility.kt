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

package org.jetbrains.kotlin.descriptors

import org.jetbrains.kotlin.resolve.DescriptorUtils
import org.jetbrains.kotlin.types.JetType
import org.jetbrains.kotlin.types.TypeConstructor

sealed class EffectiveVisibility(val name: String) {

    override fun toString() = name

    object Private : EffectiveVisibility("private") {
        override fun relation(other: EffectiveVisibility) =
                if (this == other) Relation.SAME else Relation.WORSE
    }

    object Public : EffectiveVisibility("public") {
        override fun relation(other: EffectiveVisibility) =
                if (this == other) Relation.SAME else Relation.BETTER
    }

    object Internal : EffectiveVisibility("internal") {
        override fun relation(other: EffectiveVisibility) =
                if (other == Public || other == Private) !other.relation(this)
                else super.relation(other)
    }

    class Protected(val container: ClassDescriptor?) : EffectiveVisibility("protected") {

        override fun equals(other: Any?) = (other is Protected && container == other.container)

        override fun hashCode() = container?.hashCode() ?: 0

        override fun toString() = "${super.toString()}(${container?.name ?: '?'})"

        override fun relation(other: EffectiveVisibility) =
                if (other == Public || other == Private) {
                    !other.relation(this)
                }
                else if (this == other) {
                    Relation.SAME
                }
                else if (other is Protected) {
                    if (container == null || other.container == null) {
                        Relation.UNKNOWN
                    }
                    else if (DescriptorUtils.isSubclass(container, other.container)) {
                        Relation.WORSE
                    }
                    else if (DescriptorUtils.isSubclass(other.container, container)) {
                        Relation.BETTER
                    }
                    else {
                        Relation.UNKNOWN
                    }
                }
                else {
                    Relation.UNKNOWN
                }
    }

    private enum class Relation {
        WORSE,
        SAME,
        BETTER,
        UNKNOWN;

        operator fun not() = when (this) {
            WORSE -> BETTER
            BETTER -> WORSE
            else -> this
        }
    }

    open fun relation(other: EffectiveVisibility) =
            if (this == other) Relation.SAME else Relation.UNKNOWN

    fun sameOrBetter(other: EffectiveVisibility) = when (relation(other)) {
        Relation.SAME, Relation.BETTER -> true
        Relation.WORSE, Relation.UNKNOWN -> false
    }

    fun lowerBound(other: EffectiveVisibility) = when (relation(other)) {
        Relation.SAME, Relation.WORSE -> this
        Relation.BETTER -> other
        Relation.UNKNOWN -> Private
    }

    companion object {

        private fun lowerBound(first: EffectiveVisibility, vararg args: EffectiveVisibility) =
            lowerBound(first, args.asList())

        private fun lowerBound(first: EffectiveVisibility, args: List<EffectiveVisibility>) =
                args.fold(first, { x, y -> x.lowerBound(y) })

        private fun Visibility.forVisibility(descriptor: ClassDescriptor? = null) = when (this) {
            Visibilities.PRIVATE, Visibilities.PRIVATE_TO_THIS -> Private
            Visibilities.PROTECTED -> Protected(descriptor)
            Visibilities.INTERNAL -> Internal
            Visibilities.PUBLIC -> Public
            // Considered effectively public
            Visibilities.LOCAL -> Public
            else -> this.effectiveVisibility(descriptor)
        }

        private fun ClassifierDescriptor.forClassifier(classes: Set<ClassDescriptor>): EffectiveVisibility =
                lowerBound(if (this is ClassDescriptor) this.forClass(classes) else Public,
                           (this.containingDeclaration as? ClassifierDescriptor)?.forClassifier(classes) ?: Public)

        fun ClassDescriptor.forClass() = forClass(emptySet())

        private fun ClassDescriptor.forClass(classes: Set<ClassDescriptor>) =
                if (this in classes) Public
                else lowerBound(visibility.forVisibility(this.containingDeclaration as? ClassDescriptor),
                                defaultType.constructor.parameters.map {
                                    typeConstructor.forTypeConstructor(classes + this)
                                })

        fun JetType.forType() = constructor.forTypeConstructor(emptySet())

        private fun TypeConstructor.forTypeConstructor(classes: Set<ClassDescriptor>): EffectiveVisibility =
                lowerBound(this.declarationDescriptor?.forClassifier(classes) ?: Public,
                           parameters.map { it.typeConstructor.forTypeConstructor(classes) })

        fun MemberDescriptor.forMember() =
                lowerBound(visibility.forVisibility(this.containingDeclaration as? ClassDescriptor),
                           (this.containingDeclaration as? ClassDescriptor)?.forClass() ?: Public)
    }
}