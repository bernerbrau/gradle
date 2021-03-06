/*
 * Copyright 2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.api.file;

import org.gradle.api.Incubating;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;

import java.io.File;

/**
 * Represents some configurable regular file location, whose value is mutable and not necessarily currently known until later.
 * <p>
 * <b>Note:</b> This interface is not intended for implementation by build script or plugin authors. An instance of this class can be created using the {@link org.gradle.api.model.ObjectFactory#fileProperty()} method.
 *
 * @since 4.3
 */
@Incubating
public interface RegularFileProperty extends Provider<RegularFile>, Property<RegularFile> {
    /**
     * Views the location of this file as a {@link File}.
     */
    Provider<File> getAsFile();

    /**
     * Sets the location of this file.
     */
    void set(File file);
}
