package org.codehaus.mojo.exec.project5lib;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Used for manual integrationtest of the java goal.
 *
 */
public class ExampleLibrary
{
    static Logger log = Logger.getLogger(ExampleLibrary.class.getName());
    public ExampleLibrary() {
    }
    public boolean isAGoodDay() {
	    log.log(Level.FINE, "It's a great day!");
	    return true;
    }
}
