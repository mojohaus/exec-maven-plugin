package org.codehaus.mojo.exec;

import org.apache.maven.artifact.Artifact;

/**
 * Created by IntelliJ IDEA.
 * User: U424552
 * Date: Jun 21, 2006
 * Time: 3:05:00 PM
 * To change this template use File | Settings | File Templates.
 */
public class ExecutableDependency
{
    private String groupId;

    private String artifactId;

    public ExecutableDependency()
    {
    }

    public String getGroupId()
    {
        return this.groupId;
    }

    public void setGroupId( String groupId )
    {
        this.groupId = groupId;
    }

    public String getArtifactId()
    {
        return this.artifactId;
    }

    public void setArtifactId( String artifactId )
    {
        this.artifactId = artifactId;
    }

    public boolean matches( Artifact artifact )
    {
        return artifact.getGroupId().equals( this.getGroupId() ) &&
            artifact.getArtifactId().equals( this.getArtifactId() );
    }

    public String toString()
    {
        return this.groupId + ":" + this.artifactId;
    }

    public boolean equals( Object o )
    {
        if ( this == o )
        {
            return true;
        }
        if ( o == null || getClass() != o.getClass() )
        {
            return false;
        }

        final ExecutableDependency that = (ExecutableDependency) o;

        if ( artifactId != null ? !artifactId.equals( that.artifactId ) : that.artifactId != null )
        {
            return false;
        }
        if ( groupId != null ? !groupId.equals( that.groupId ) : that.groupId != null )
        {
            return false;
        }

        return true;
    }

    public int hashCode()
    {
        int result;
        result = ( groupId != null ? groupId.hashCode() : 0 );
        result = 29 * result + ( artifactId != null ? artifactId.hashCode() : 0 );
        return result;
    }
}
