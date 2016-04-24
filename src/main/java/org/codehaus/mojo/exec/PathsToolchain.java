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

import java.io.File;
import java.util.List;

import org.apache.maven.toolchain.DefaultToolchain;
import org.apache.maven.toolchain.model.ToolchainModel;
import org.codehaus.plexus.logging.Logger;
import org.codehaus.plexus.util.Os;

/**
 * Searches a list of configured paths for the requested tool.
 *
 * @author Markus KARG (markus@headcrashing.eu)
 */
class PathsToolchain extends DefaultToolchain {
	private List<String> paths;

	public PathsToolchain(final ToolchainModel model, final Logger logger) {
		super(model, "paths", logger); // NOI18N
	}

	public List<String> getPaths() {
		return this.paths;
	}

	public void setPaths(final List<String> paths) {
		this.paths = paths;
	}

	@Override
	public String toString() {
		return "Paths" + this.getPaths(); // NOI18N
	}

	public String findTool(final String toolName) {
		for (final String path : this.paths) {
			final File tool = findTool(toolName, new File(path));
			if (tool != null)
				return tool.getAbsolutePath();
		}

		return null;
	}

	private static File findTool(final String toolName, final File folder) {
		final File tool = new File(folder, toolName + (Os.isFamily("windows") && !toolName.contains(".") ? ".exe" : "")); // NOI18N

		return tool.exists() ? tool : null;
	}
}