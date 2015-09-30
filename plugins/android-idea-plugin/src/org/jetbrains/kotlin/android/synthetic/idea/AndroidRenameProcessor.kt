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

import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleServiceManager
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.psi.PsiElement
import com.intellij.psi.xml.XmlAttribute
import com.intellij.psi.xml.XmlAttributeValue
import com.intellij.refactoring.rename.RenamePsiElementProcessor
import org.jetbrains.android.dom.wrappers.ValueResourceElementWrapper
import org.jetbrains.android.util.AndroidResourceUtil
import org.jetbrains.kotlin.android.synthetic.idToName
import org.jetbrains.kotlin.android.synthetic.isAndroidSyntheticElement
import org.jetbrains.kotlin.android.synthetic.nameToIdDeclaration
import org.jetbrains.kotlin.android.synthetic.res.SyntheticFileGenerator
import org.jetbrains.kotlin.asJava.namedUnwrappedElement
import org.jetbrains.kotlin.idea.caches.resolve.ModuleSourceInfo
import org.jetbrains.kotlin.idea.caches.resolve.getModuleInfo
import org.jetbrains.kotlin.psi.JetProperty

public class AndroidRenameProcessor : RenamePsiElementProcessor() {

    override fun canProcessElement(element: PsiElement): Boolean {
        return (element.namedUnwrappedElement is JetProperty &&
                isAndroidSyntheticElement(element.namedUnwrappedElement)) || element is XmlAttributeValue
    }

    private fun PsiElement.getModule(): Module? {
        val moduleInfo = getModuleInfo()
        return if (moduleInfo is ModuleSourceInfo) moduleInfo.module else null
    }

    override fun prepareRenaming(element: PsiElement, newName: String, allRenames: MutableMap<PsiElement, String>) {
        super.prepareRenaming(element, newName, allRenames)

        if (element.namedUnwrappedElement is JetProperty) {
            renameSyntheticProperty(element.namedUnwrappedElement as JetProperty, newName, allRenames)
        }
        else if (element is XmlAttributeValue) {
            renameAttributeValue(element, newName, allRenames)
        }
    }

    private fun renameSyntheticProperty(jetProperty: JetProperty, newName: String, allRenames: MutableMap<PsiElement, String>) {
        val module = jetProperty.getModule() ?: return

        val processor = ModuleServiceManager.getService(module, SyntheticFileGenerator::class.java) ?: return
        val resourceManager = processor.layoutXmlFileManager

        val attributes = resourceManager.propertyToXmlAttributes(jetProperty).map { it as? XmlAttribute }.filterNotNull()
        val attributeValueNewName = nameToIdDeclaration(newName)

        for (attribute in attributes) {
            val attributeValue = attribute.valueElement ?: continue
            allRenames[attributeValue] = attributeValueNewName

            val name = AndroidResourceUtil.getResourceNameByReferenceText(newName) ?: return
            for (resField in AndroidResourceUtil.findIdFields(attribute)) {
                allRenames.put(resField, AndroidResourceUtil.getFieldNameByResourceName(name))
            }
        }
    }

    private fun renameAttributeValue(attribute: XmlAttributeValue, newName: String, allRenames: MutableMap<PsiElement, String>) {
        val module = attribute.getModule() ?: ModuleUtilCore.findModuleForFile(
                attribute.containingFile.virtualFile, attribute.project) ?: return

        val processor = ModuleServiceManager.getService(module, SyntheticFileGenerator::class.java)!!
        val oldPropName = AndroidResourceUtil.getResourceNameByReferenceText(attribute.value)
        val newPropName = idToName(newName)
        if (oldPropName != null && newPropName != null) {
            allRenames.keySet()
                    .filter { it is ValueResourceElementWrapper || it is XmlAttributeValue }
                    .forEach { allRenames.remove(it) }

            renameSyntheticProperties(allRenames, newPropName, oldPropName, processor)
            allRenames[ValueResourceElementWrapper(attribute)] = newName
        }
    }

    private fun renameSyntheticProperties(
            allRenames: MutableMap<PsiElement, String>,
            newPropName: String,
            oldPropName: String,
            processor: SyntheticFileGenerator
    ) {
        val props = processor.getSyntheticFiles().flatMap { it.findChildrenByClass(JetProperty::class.java).toList() }
        val matchedProps = props.filter { it.name == oldPropName }
        for (prop in matchedProps) {
            allRenames[prop] = newPropName
        }
    }
}