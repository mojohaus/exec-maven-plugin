package org.codehaus.mojo.exec;

/*
 * Copyright MojoHaus and Contributors
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

/**
 * Exception to be thrown by {@link SystemExitManager} when {@link System#exit(int)} is called
 * 
 * @author Alexander Kriegisch
 */
public class SystemExitException extends SecurityException
{
    private final int exitCode;

    public SystemExitException( String s, int exitCode )
    {
        super( s );
        this.exitCode = exitCode;
    }

    public int getExitCode()
    {
        return exitCode;
    }
}
