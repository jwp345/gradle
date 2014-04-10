/*
 * Copyright 2013 the original author or authors.
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
package org.gradle.nativebinaries.language.c.internal.incremental;

import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.changedetection.state.FileSnapshotter;
import org.gradle.api.internal.changedetection.state.TaskArtifactStateCacheAccess;
import org.gradle.api.internal.tasks.SimpleWorkResult;
import org.gradle.api.internal.tasks.compile.Compiler;
import org.gradle.api.tasks.WorkResult;
import org.gradle.cache.PersistentIndexedCache;
import org.gradle.cache.PersistentStateCache;
import org.gradle.internal.Factory;
import org.gradle.language.jvm.internal.SimpleStaleClassCleaner;
import org.gradle.nativebinaries.toolchain.internal.NativeCompileSpec;
import org.gradle.util.CollectionUtils;

import java.io.File;

public class IncrementalNativeCompiler implements Compiler<NativeCompileSpec> {
    private final Compiler<NativeCompileSpec> delegateCompiler;
    private final TaskInternal task;
    private final SourceIncludesParser sourceIncludesParser;
    private final TaskArtifactStateCacheAccess cacheAccess;
    private final FileSnapshotter fileSnapshotter;

    public IncrementalNativeCompiler(TaskInternal task, SourceIncludesParser sourceIncludesParser,
                                     TaskArtifactStateCacheAccess cacheAccess, FileSnapshotter fileSnapshotter, Compiler<NativeCompileSpec> delegateCompiler) {
        this.task = task;
        this.sourceIncludesParser = sourceIncludesParser;
        this.cacheAccess = cacheAccess;
        this.fileSnapshotter = fileSnapshotter;
        this.delegateCompiler = delegateCompiler;
    }

    public WorkResult execute(final NativeCompileSpec spec) {
        return cacheAccess.useCache("incremental compile", new Factory<WorkResult>() {
            public WorkResult create() {
                IncrementalCompileProcessor processor = createProcessor(spec.getIncludeRoots());
                if (spec.isIncrementalCompile()) {
                    return doIncrementalCompile(processor, spec);
                }
                return doCleanIncrementalCompile(processor, spec);
            }
        });
    }

    protected WorkResult doIncrementalCompile(IncrementalCompileProcessor processor, NativeCompileSpec spec) {
        IncrementalCompilation compilation = processor.processSourceFiles(spec.getSourceFiles());

        // Determine the actual sources to clean/compile
        spec.setSourceFiles(compilation.getRecompile());
        spec.setRemovedSourceFiles(compilation.getRemoved());
        return delegateCompiler.execute(spec);
    }


    protected WorkResult doCleanIncrementalCompile(IncrementalCompileProcessor processor, NativeCompileSpec spec) {
        processor.processSourceFiles(spec.getSourceFiles());
        boolean deleted = cleanPreviousOutputs(spec);
        WorkResult compileResult = delegateCompiler.execute(spec);
        if (deleted && !compileResult.getDidWork()) {
            return new SimpleWorkResult(deleted);
        }
        return compileResult;
    }

    private boolean cleanPreviousOutputs(NativeCompileSpec spec) {
        SimpleStaleClassCleaner cleaner = new SimpleStaleClassCleaner(getTask().getOutputs());
        cleaner.setDestinationDir(spec.getObjectFileDir());
        cleaner.execute();
        return cleaner.getDidWork();
    }

    protected TaskInternal getTask() {
        return task;
    }

    private IncrementalCompileProcessor createProcessor(Iterable<File> includes) {
        PersistentStateCache<CompilationState> compileStateCache = createCompileStateCache(task.getPath());

        DefaultSourceIncludesResolver dependencyParser = new DefaultSourceIncludesResolver(CollectionUtils.toList(includes));

        return new IncrementalCompileProcessor(compileStateCache, dependencyParser, sourceIncludesParser, fileSnapshotter);
    }

    private PersistentStateCache<CompilationState> createCompileStateCache(final String taskPath) {
        final PersistentIndexedCache<String, CompilationState> stateIndexedCache = cacheAccess.createCache("compilationState", String.class, new CompilationStateSerializer());
        return new PersistentStateCache<CompilationState>() {
            public CompilationState get() {
                return stateIndexedCache.get(taskPath);
            }

            public void set(CompilationState newValue) {
                stateIndexedCache.put(taskPath, newValue);
            }

            public void update(UpdateAction<CompilationState> updateAction) {
                throw new UnsupportedOperationException();
            }
        };
    }
}
