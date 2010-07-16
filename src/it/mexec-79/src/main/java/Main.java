import java.io.FileWriter;
import java.io.Writer;
import java.net.URL;

public class Main
{
    public static void main( String[] args ) throws Exception
    {
        URL url = Main.class.getClassLoader().getResource( "messages.properties" );
        Writer writer = new FileWriter( "exec.log" );
        try {
            writer.write( url.toString() );
        }
        finally {
            writer.close();
        }
    }
}
