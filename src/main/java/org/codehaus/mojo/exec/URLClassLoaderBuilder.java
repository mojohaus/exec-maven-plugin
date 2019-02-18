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

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * 
 * @author Robert Scholte
 * @since 3.0.0
 */
class URLClassLoaderBuilder
{
    private Collection<Path> paths;

    private URLClassLoaderBuilder()
    {
    }

    static URLClassLoaderBuilder builder()
    {
        return new URLClassLoaderBuilder();
    }

    URLClassLoaderBuilder setPaths( Collection<Path> paths )
    {
        this.paths = paths;
        return this;
    }

    ClassLoader build() throws IOException
    {
        List<URL> urls = new ArrayList<>( paths.size() );

        for ( Path dependency : paths )
        {
            try
            {
                urls.add( dependency.toUri().toURL() );
            }
            catch ( MalformedURLException e )
            {
                throw new IOException( "Error during setting up classpath", e );
            }
        }

        return new URLClassLoader( urls.toArray( new URL[0] ) );
    }

}
