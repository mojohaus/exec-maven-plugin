package org.codehaus.mojo.exec.project5lib;

import org.apache.log4j.Logger;

/**
 * Used for manual integrationtest of the java goal.
 *
 */
public class ExampleLibrary
{
    static Logger log = Logger.getLogger(ExampleLibrary.class);
    public ExampleLibrary() {
    }
    public boolean isAGoodDay() {
	    log.debug("It's a great day!");
	    return true;
    }
}
