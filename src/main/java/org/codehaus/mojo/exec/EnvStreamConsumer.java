package org.codehaus.mojo.exec;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.codehaus.plexus.util.StringUtils;
import org.codehaus.plexus.util.cli.StreamConsumer;

public class EnvStreamConsumer
    implements StreamConsumer
{

    public static final String START_PARSING_INDICATOR =
        "================================This is the beginning of env parsing================================";

    private Map<String, String> envs = new HashMap<String, String>();

    private List<String> unparsed = new ArrayList<String>();

    private boolean startParsing = false;

    public void consumeLine( String line )
    {

        if ( line.startsWith( START_PARSING_INDICATOR ) )
        {
            this.startParsing = true;
            return;
        }

        if ( this.startParsing )
        {
            String[] tokens = StringUtils.split( line, "=", 2 );
            if ( tokens.length == 2 )
            {
                envs.put( tokens[0], tokens[1] );
            } else {
                // Don't hide an environment variable with no value e.g. APP_OVERRIDE=
                String trimmedLine = line.trim();
                if ( trimmedLine.endsWith("=") )
                {
                    envs.put( trimmedLine.substring( 0, ( trimmedLine.length() - 1 ) ), null );
                }
                else
                {
                    unparsed.add( line );
                }
            }
        }
        else
        {
            System.out.println( line );
        }

    }

    public Map<String, String> getParsedEnv()
    {
        return this.envs;
    }

    public List<String> getUnparsedLines()
    {
        return this.unparsed;
    }
}
