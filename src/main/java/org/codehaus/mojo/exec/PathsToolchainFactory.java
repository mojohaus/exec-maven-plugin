package org.codehaus.mojo.exec;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import javax.inject.Named;
import javax.inject.Singleton;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.apache.maven.toolchain.MisconfiguredToolchainException;
import org.apache.maven.toolchain.ToolchainFactory;
import org.apache.maven.toolchain.ToolchainPrivate;
import org.apache.maven.toolchain.model.ToolchainModel;
import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.xml.Xpp3Dom;

/**
 * Factory for {@link PathsToolchain}.
 *
 * @author Markus KARG (markus@headcrashing.eu)
 */
@Named("paths")
@Singleton
class PathsToolchainFactory implements ToolchainFactory {

    @Override
    public ToolchainPrivate createToolchain(final ToolchainModel model) throws MisconfiguredToolchainException {
        if (model == null) return null;

        final PathsToolchain pathsToolchain = new PathsToolchain(model);
        final Xpp3Dom config = (Xpp3Dom) model.getConfiguration();
        if (config == null) return pathsToolchain;

        final Xpp3Dom pathDom = config.getChild("paths");
        if (pathDom == null) {
            throw new MisconfiguredToolchainException("paths element is empty");
        }

        final Xpp3Dom[] pathDoms = pathDom.getChildren("path");
        if (pathDoms == null || pathDoms.length == 0) {
            throw new MisconfiguredToolchainException("paths -> path elements are not present");
        }

        final List<String> paths = new ArrayList<>(pathDoms.length);
        for (final Xpp3Dom pathdom : pathDoms) {
            final String pathString = pathdom.getValue();

            if (pathString == null) throw new MisconfiguredToolchainException("path element is empty");

            final String normalizedPath = FileUtils.normalize(pathString);
            final File file = new File(normalizedPath);
            if (!file.exists())
                throw new MisconfiguredToolchainException("Non-existing path '" + file.getAbsolutePath() + "'");

            paths.add(normalizedPath);
        }

        pathsToolchain.setPaths(paths);

        return pathsToolchain;
    }

    @Override
    public ToolchainPrivate createDefaultToolchain() {
        return null;
    }
}
