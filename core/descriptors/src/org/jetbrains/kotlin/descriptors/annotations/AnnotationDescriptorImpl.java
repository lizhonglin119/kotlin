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

package org.jetbrains.kotlin.descriptors.annotations;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.kotlin.descriptors.SourceElement;
import org.jetbrains.kotlin.descriptors.ValueParameterDescriptor;
import org.jetbrains.kotlin.renderer.DescriptorRenderer;
import org.jetbrains.kotlin.resolve.constants.ConstantValue;
import org.jetbrains.kotlin.types.JetType;

import java.util.Collections;
import java.util.Map;

public class AnnotationDescriptorImpl implements AnnotationDescriptor {
    private final JetType annotationType;
    private final Map<ValueParameterDescriptor, ConstantValue<?>> valueArguments;
    private final SourceElement source;

    public AnnotationDescriptorImpl(
            @NotNull JetType annotationType,
            @NotNull Map<ValueParameterDescriptor, ConstantValue<?>> valueArguments,
            @NotNull SourceElement source
    ) {
        this.annotationType = annotationType;
        this.valueArguments = Collections.unmodifiableMap(valueArguments);
        this.source = source;
    }

    @Override
    @NotNull
    public JetType getType() {
        return annotationType;
    }

    @Override
    @NotNull
    public Map<ValueParameterDescriptor, ConstantValue<?>> getAllValueArguments() {
        return valueArguments;
    }

    @Override
    @NotNull
    public SourceElement getSource() {
        return source;
    }

    @Override
    public String toString() {
        return DescriptorRenderer.Companion.getFQ_NAMES_IN_TYPES().renderAnnotation(this, null);
    }
}
