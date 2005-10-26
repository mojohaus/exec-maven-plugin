package org.codehaus.mojo.exec;

/*
 * Copyright 2005 The Codehaus.
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

import java.util.Collection;

/**
 *
 * @author Jerome Lacoste <jerome@coffeebreaks.org>
 */
public class Classpath {
    /**
     * @parameter dependency
     */
    private Collection dependencies;

    public void setDependencies( Collection dependencies ) {
        this.dependencies = dependencies;
    }

    public Collection getDependencies() {
        return dependencies;
    }
}
