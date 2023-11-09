import java.util.Arrays;

public class Main
{
    public static void main( String[] args )
    {
        System.out.println( Arrays.toString( args ) );
        switch ( System.getProperty( "exitBehaviour", "ok" ) )
        {
            case "throw-exception":
                throw new RuntimeException( "uh-oh" );
            case "system-exit-ok":
                System.exit( 0 );
            case "system-exit-error":
                System.exit( 123 );
        }
        System.out.println( "OK" );
    }
}
