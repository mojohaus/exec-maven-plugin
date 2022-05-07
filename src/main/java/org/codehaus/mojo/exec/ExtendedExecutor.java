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

import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.OS;
import org.apache.commons.exec.launcher.VmsCommandLauncher;

import java.io.File;
import java.io.IOException;
import java.util.Map;

/**
 * An executor which has the ability to use the {@link ProcessBuilder#inheritIO()} flag.
 *
 * @author Guillaume Nodet (gnodet@gmail.com)
 */
public class ExtendedExecutor extends DefaultExecutor
{

    private final boolean inheritIo;

    public ExtendedExecutor( boolean inheritIo )
    {
        this.inheritIo = inheritIo;
    }

    @Override
    protected Process launch( CommandLine command, Map<String, String> env, File dir ) throws IOException
    {
        if ( dir != null && !dir.exists() )
        {
            throw new IOException( dir + " doesn't exist." );
        }
        if ( OS.isFamilyOpenVms() )
        {
            return new VmsCommandLauncher().exec( command, env, dir );
        }
        else
        {
            ProcessBuilder pb = new ProcessBuilder( command.toStrings() );
            for ( Map.Entry<String, String> entry : env.entrySet() )
            {
                String key = entry.getKey() != null ? entry.getKey() : "";
                String val = entry.getValue() != null ? entry.getValue() : "";
                pb.environment().put( key, val );
            }
            pb.directory( dir );
            if ( inheritIo )
            {
                pb.inheritIO();
            }
            return pb.start();
        }
    }

}
