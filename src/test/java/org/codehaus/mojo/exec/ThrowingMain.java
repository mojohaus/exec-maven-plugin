/**
 * Copyright 2016 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.codehaus.mojo.exec;

import java.io.IOException;

/**
 * Executable used to test applications which exit exceptionally.
 *
 * @author Lukasz Cwik
 */
public class ThrowingMain
{
    public static void main( String... args )
        throws Exception
    {
        throw new IOException( "expected IOException thrown by test" );
    }
}

