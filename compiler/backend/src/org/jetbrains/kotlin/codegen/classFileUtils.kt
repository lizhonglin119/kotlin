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

package org.jetbrains.kotlin.codegen

import org.jetbrains.kotlin.backend.common.output.OutputFile
import org.jetbrains.kotlin.codegen.state.GenerationState
import org.jetbrains.kotlin.load.kotlin.ModuleMapping
import org.jetbrains.kotlin.load.kotlin.PackageParts

fun ClassFileFactory.getClassFiles(): Iterable<OutputFile> {
    return asList().filterClassFiles()
}

fun List<OutputFile>.filterClassFiles(): Iterable<OutputFile> {
    return filter { it.relativePath.endsWith(".class") }
}

fun List<PackageParts>.addCompiledPartsAndSort(state: GenerationState): List<PackageParts> =
        addCompiledParts(state).sortedBy { it.packageFqName }

private fun List<PackageParts>.addCompiledParts(state: GenerationState): List<PackageParts> {
    if (state.incrementalCompilationComponents == null || state.moduleId == null) return this

    val incrementalCache = state.incrementalCompilationComponents.getIncrementalCache(state.moduleId)
    val moduleMappingData = incrementalCache.getModuleMappingData() ?: return this

    val mapping = ModuleMapping.create(moduleMappingData)

    val obsoletePackageParts = incrementalCache.getObsoletePackageParts().map {
        val i = it.lastIndexOf('/')
        // TODO test for `/` -> `.`
        Pair(if (i < 0) "" else it.substring(0, i).replace('/', '.'), it.substring(i + 1))
    }

    for ((qualifier, name) in obsoletePackageParts) {
        mapping.findPackageParts(qualifier)?.run { parts.remove(name) }
    }

    return (this + mapping.packageFqName2Parts.values())
            .groupBy { it.packageFqName }
            .map {
                val (packageFqName, packageParts) = it
                val newPackageParts = PackageParts(packageFqName)
                packageParts.forEach { newPackageParts.parts.addAll(it.parts) }
                newPackageParts
            }
}
