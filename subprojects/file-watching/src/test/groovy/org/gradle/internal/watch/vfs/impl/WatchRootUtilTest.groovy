/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.internal.watch.vfs.impl

import org.gradle.internal.file.FileMetadataSnapshot.AccessType
import org.gradle.internal.file.impl.DefaultFileMetadataSnapshot
import org.gradle.internal.hash.Hashing
import org.gradle.internal.snapshot.CompleteDirectorySnapshot
import org.gradle.internal.snapshot.PathUtil
import org.gradle.internal.snapshot.RegularFileSnapshot
import org.gradle.internal.watch.registry.impl.WatchRootUtil
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import spock.lang.Specification
import spock.lang.Unroll

import java.nio.file.Paths

@Unroll
class WatchRootUtilTest extends Specification {
    @Requires(TestPrecondition.UNIX_DERIVATIVE)
    def "resolves recursive UNIX roots #directories to #resolvedRoots"() {
        expect:
        resolveRecursiveRoots(directories) == resolvedRoots

        where:
        directories        | resolvedRoots
        []                 | []
        ["/a"]             | ["/a"]
        ["/a", "/b"]       | ["/a", "/b"]
        ["/a", "/a/b"]     | ["/a"]
        ["/a/b", "/a"]     | ["/a"]
        ["/a", "/a/b/c/d"] | ["/a"]
        ["/a/b/c/d", "/a"] | ["/a"]
        ["/a", "/b/a"]     | ["/a", "/b/a"]
        ["/b/a", "/a"]     | ["/a", "/b/a"]
    }

    @Requires(TestPrecondition.WINDOWS)
    def "resolves recursive Windows roots #directories to #resolvedRoots"() {
        expect:
        resolveRecursiveRoots(directories) == resolvedRoots

        where:
        directories                 | resolvedRoots
        []                          | []
        ["C:\\a"]                   | ["C:\\a"]
        ["C:\\a", "C:\\b"]          | ["C:\\a", "C:\\b"]
        ["C:\\a", "C:\\a\\b"]       | ["C:\\a"]
        ["C:\\a\\b", "C:\\a"]       | ["C:\\a"]
        ["C:\\a", "C:\\a\\b\\c\\d"] | ["C:\\a"]
        ["C:\\a\\b\\c\\d", "C:\\a"] | ["C:\\a"]
        ["C:\\a", "C:\\b\\a"]       | ["C:\\a", "C:\\b\\a"]
        ["C:\\b\\a", "C:\\a"]       | ["C:\\a", "C:\\b\\a"]
    }

    def "resolves directories to watch from snapshot"() {
        when:
        def directoriesToWatch = WatchRootUtil.getDirectoriesToWatch(snapshot).collect { it.toString() } as Set
        then:
        normalizeLineSeparators(directoriesToWatch) == (expectedDirectoriesToWatch as Set)

        where:
        snapshot                                       | expectedDirectoriesToWatch
        fileSnapshot('/some/absolute/parent/file')     | ['/some/absolute/parent']
        directorySnapshot('/some/absolute/parent/dir') | ['/some/absolute/parent', '/some/absolute/parent/dir']
    }

    private static RegularFileSnapshot fileSnapshot(String absolutePath) {
        new RegularFileSnapshot(absolutePath, absolutePath.substring(absolutePath.lastIndexOf('/') + 1), Hashing.md5().hashString(absolutePath), DefaultFileMetadataSnapshot.file(1, 1, AccessType.DIRECT))
    }

    private static CompleteDirectorySnapshot directorySnapshot(String absolutePath) {
        new CompleteDirectorySnapshot(absolutePath, PathUtil.getFileName(absolutePath), [], Hashing.md5().hashString(absolutePath), AccessType.DIRECT)
    }

    private static List<String> resolveRecursiveRoots(List<String> directories) {
        WatchRootUtil.resolveRootsToWatch(directories.collect { Paths.get(it) } as Set)
            .collect { it.toString() }
            .sort()
    }

    private static Set<String> normalizeLineSeparators(Set<String> paths) {
        return paths*.replace(File.separatorChar, '/' as char) as Set
    }
}
