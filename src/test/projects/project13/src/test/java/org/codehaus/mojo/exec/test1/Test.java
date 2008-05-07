package org.codehaus.mojo.exec.test1;

/*
 * Copyright 2005-2008 The Codehaus.
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

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;

import java.util.Iterator;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Properties;
import junit.framework.Assert;

/**
 * @author Jerome Lacoste <jerome@coffeebreaks.org>
 * @version $Id: Test.java 6588 2008-03-28 12:22:57Z bentmann $
 */
public class Test
{
    // test that we can use FileUtils, i.e. that classpath specification works
    // Write to the file specified as args[ 0 ], the following (sorted)
    // * the remaining arguments prefixed with "arg."
    // * the project properties (identified in the System properties by their "project." prefix),
    //   in the form of "key=value"
    public static void main( String[] args ) throws Exception {

         // make sure junit classes are in the classpath
         Assert.assertTrue( "missing output file path" , args.length > 0 ) ;

         List myProperties = new ArrayList();
         for ( int i = 0; i < args.length - 1; i++ ) {
             myProperties.add( "arg." + args[ i + 1 ] );
         }

         Properties systemProperties = System.getProperties();
         for ( Iterator it = systemProperties.keySet().iterator(); it.hasNext(); ) {
              String key = it.next().toString();
              if ( key.startsWith( "project." ) ) {
                  myProperties.add( key + "=" + systemProperties.get( key ) );
              }
         }
         Collections.sort( myProperties );
         File toFile = new File( args[0] );
         toFile.getParentFile().mkdirs();
         FileUtils.writeLines( new File( args[0] ), "UTF-8", myProperties );
    }
}
