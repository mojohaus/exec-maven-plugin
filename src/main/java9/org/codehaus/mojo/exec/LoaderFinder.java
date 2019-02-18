package org.codehaus.mojo.exec;

import java.io.IOException;

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

import java.lang.module.Configuration;
import java.lang.module.ModuleFinder;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

class LoaderFinder
{
    static ClassLoader find( List<Path> dependencies, String mainClass ) throws IOException
    {
        int sepIndex= mainClass.indexOf( '/' );

        final String bootClass;
        final String moduleName;
        if ( sepIndex < 0 )
        {
            return URLClassLoaderBuilder.builder().setPaths( dependencies ).build();
        }
        else
        {
            moduleName = mainClass.substring( 0, sepIndex );

            bootClass = mainClass.substring( sepIndex + 1 );
        }
        
        ModuleFinder finder = ModuleFinder.of( dependencies.toArray( new Path[0] ) );

        ModuleLayer parent = ModuleLayer.boot();

        Configuration cf = parent.configuration().resolve( finder, ModuleFinder.of(), Set.of( moduleName ) );

        ClassLoader scl = ClassLoader.getSystemClassLoader();
        
        ModuleLayer.Controller controller = ModuleLayer.defineModulesWithOneLoader( cf, Arrays.asList( parent ), scl );

        try
        {
            ModuleLayer layer = controller.layer();

            String mainPackageName = layer.findLoader( moduleName ).loadClass( bootClass ).getPackageName();

            Module source = layer.findModule(moduleName).orElseThrow( null );

            Module target = ExecJavaMojo.class.getModule();
            
            controller = controller.addExports( source, mainPackageName, target );
        }
        catch ( ClassNotFoundException e )
        {
            // noop, will be thrown when trying to execute the main class
        }

        return controller.layer().findLoader( moduleName );
    }
    
}
