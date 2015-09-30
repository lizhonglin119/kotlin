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

package org.jetbrains.kotlin.android.synthetic.idea

import com.intellij.openapi.application.QueryExecutorBase
import com.intellij.openapi.module.ModuleServiceManager
import com.intellij.psi.PsiReference
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.xml.XmlAttribute
import com.intellij.util.Processor
import org.jetbrains.kotlin.android.synthetic.isAndroidSyntheticElement
import org.jetbrains.kotlin.android.synthetic.res.SyntheticFileGenerator
import org.jetbrains.kotlin.idea.caches.resolve.ModuleSourceInfo
import org.jetbrains.kotlin.idea.caches.resolve.getModuleInfo
import org.jetbrains.kotlin.psi.JetProperty

public class AndroidExtensionsReferencesSearcher : QueryExecutorBase<PsiReference, ReferencesSearch.SearchParameters>() {
    override fun processQuery(queryParameters: ReferencesSearch.SearchParameters, consumer: Processor<PsiReference>) {
        val property = queryParameters.elementToSearch as? JetProperty ?: return
        if (!isAndroidSyntheticElement(property)) return

        val moduleInfo = property.getModuleInfo() as? ModuleSourceInfo ?: return

        val generator = ModuleServiceManager.getService(moduleInfo.module, SyntheticFileGenerator::class.java) ?: return
        for (attribute in generator.layoutXmlFileManager.propertyToXmlAttributes(property)) {
            val xmlAttribute = attribute as? XmlAttribute ?: continue
            val valueElement = xmlAttribute.valueElement ?: continue
            val ref = XmlValueElementWrapper(valueElement).reference ?: continue
            if (!consumer.process(ref)) break
        }
    }
}