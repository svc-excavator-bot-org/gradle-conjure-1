/*
 * (c) Copyright 2018 Palantir Technologies Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.palantir.gradle.conjure;

import com.google.common.collect.ImmutableList;
import com.palantir.gradle.conjure.api.ServiceDependency;
import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.SetProperty;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

@CacheableTask
public class CompileIrTask extends DefaultTask {
    private static final String EXECUTABLE = OsUtils.appendDotBatIfWindows("bin/conjure");
    private final RegularFileProperty outputIrFile = getProject().getObjects().fileProperty();

    private Supplier<File> inputDirectory;
    private Supplier<File> executableDir;
    private final SetProperty<ServiceDependency> productDependencies =
            getProject().getObjects().setProperty(ServiceDependency.class);
    private final MapProperty<String, Serializable> conjureExtensions =
            getProject().getObjects().mapProperty(String.class, Serializable.class);
    private final RegularFileProperty extensionsFile = getProject().getObjects().fileProperty();

    public CompileIrTask() {
        conjureExtensions.convention(new HashMap<>());
    }

    /**
     * Eagerly set where to output the generated IR.
     *
     * @deprecated Use {@link #getOutputIrFile()} instead.
     */
    @Deprecated
    public final void setOutputFile(File outputFile) {
        outputIrFile.set(outputFile);
    }

    /**
     * Eagerly get where to output the generated IR.
     *
     * @deprecated Use {@link #getOutputIrFile()} instead.
     */
    @Deprecated
    @Internal
    public final File getOutputFile() {
        return outputIrFile.getAsFile().get();
    }

    @OutputFile
    public final RegularFileProperty getOutputIrFile() {
        return outputIrFile;
    }

    public final void setInputDirectory(Supplier<File> inputDirectory) {
        this.inputDirectory = inputDirectory;
    }

    @InputDirectory
    @PathSensitive(PathSensitivity.RELATIVE)
    public final File getInputDirectory() {
        return inputDirectory.get();
    }

    public final void setExecutableDir(Supplier<File> executableDir) {
        this.executableDir = executableDir;
    }

    @InputDirectory
    @PathSensitive(PathSensitivity.RELATIVE)
    public final File getExecutableDir() {
        return executableDir.get();
    }

    @Input
    public final SetProperty<ServiceDependency> getProductDependencies() {
        return productDependencies;
    }

    @Input
    @Optional
    public final MapProperty<String, Serializable> getConjureExtensions() {
        return conjureExtensions;
    }

    @InputFile
    @Optional
    @PathSensitive(PathSensitivity.RELATIVE)
    public final RegularFileProperty getExtensionsFile() {
        return extensionsFile;
    }

    @TaskAction
    public final void generate() {
        File executable = new File(executableDir.get(), EXECUTABLE);
        List<String> args = ImmutableList.of(
                "compile",
                inputDirectory.get().getAbsolutePath(),
                outputIrFile.get().getAsFile().getAbsolutePath(),
                "--extensions",
                OsUtils.escapeAndWrapArgIfWindows(getSerializedExtensions()));

        GradleExecUtils.exec(getProject(), "generate conjure IR", executable, Collections.emptyList(), args);
    }

    private String getSerializedExtensions() {
        try {
            Map<Object, Object> extData = new HashMap<>();
            if (extensionsFile.isPresent()) {
                extData = GenerateConjureServiceDependenciesTask.jsonMapper.readValue(
                        extensionsFile.getAsFile().get(), Map.class);
            }
            extData.putAll(getConjureExtensions().get());
            extData.put(
                    "recommended-product-dependencies", getProductDependencies().get());
            return GenerateConjureServiceDependenciesTask.jsonMapper.writeValueAsString(extData);
        } catch (IOException e) {
            throw new RuntimeException("Failed to serialize conjure extensions", e);
        }
    }
}
