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

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.apache.maven.toolchain.ToolchainPrivate;
import org.apache.maven.toolchain.model.ToolchainModel;

/**
 * Searches a list of configured paths for the requested tool.
 *
 * @author Markus KARG (markus@headcrashing.eu)
 */
class PathsToolchain implements ToolchainPrivate {
    private final ToolchainModel model;

    private List<String> paths;

    public PathsToolchain(ToolchainModel model) {
        this.model = model;
    }

    @Override
    public ToolchainModel getModel() {
        return model;
    }

    @Override
    public String getType() {
        return model.getType();
    }

    public void setPaths(List<String> paths) {
        this.paths = paths;
    }

    private List<String> getPaths() {
        return paths != null ? paths : Collections.emptyList();
    }

    @Override
    public String findTool(final String toolName) {
        return ExecMojo.findExecutable(toolName, getPaths());
    }

    @Override
    public boolean matchesRequirements(Map<String, String> requirements) {
        return requirements.entrySet().stream()
                .anyMatch(entry -> Objects.equals(model.getProvides().get(entry.getKey()), entry.getValue()));
    }

    @Override
    public String toString() {
        return "Paths" + getPaths(); // NOI18N
    }
}
