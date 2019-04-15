import java.io.FileWriter;
import java.io.Writer;
import java.net.URL;

public class Main
{
    public static void main( String[] args ) throws Exception
    {
        /*
         * Intentionally hangs for "very long" time to simulate a completely stuck executable,
         * but let the time be so short that the IT would break "quickly" in any case.
         */
        Thread.sleep( 10000 );
    }
}
