package org.commonjava.indy.folo.action;

import org.apache.commons.io.IOUtils;
import org.commonjava.indy.core.conf.IndyDurableStateConfig;
import org.commonjava.indy.folo.data.FoloRecord;
import org.commonjava.indy.folo.data.FoloStoreToCassandra;
import org.commonjava.indy.folo.data.FoloStoretoInfinispan;
import org.commonjava.indy.folo.model.TrackedContent;
import org.commonjava.indy.folo.model.TrackedContentEntry;
import org.commonjava.indy.folo.model.TrackingKey;
import org.commonjava.indy.subsys.datafile.conf.DataFileConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.apache.commons.io.IOUtils.LINE_SEPARATOR;
import static org.commonjava.indy.core.conf.IndyDurableStateConfig.STORAGE_CASSANDRA;

public class FoloISPN2CassandraMigrationAction
{
    private final Logger logger = LoggerFactory.getLogger( getClass() );

    private final static String COMPLETED_FILE = "folo/completed.out";

    private final static String FAILED_FILE = "folo/failed.out";

    @Inject
    @FoloStoreToCassandra
    FoloRecord dbRecord;

    @Inject
    @FoloStoretoInfinispan
    FoloRecord cacheRecord;

    @Inject
    IndyDurableStateConfig durableConfig;

    @Inject
    DataFileConfiguration dataFileConfiguration;

    private volatile boolean started; // only allow one thread to run

    public boolean migrate()
    {
        if ( !STORAGE_CASSANDRA.equals( durableConfig.getFoloStorage() ) )
        {
            logger.info( "Skip the migration if the storage is not cassandra. " );
            return true;
        }
        if ( started )
        {
            logger.info( "Migration is already started. " );
            return true;
        }

        logger.info( "Migrate folo records from ISPN to cassandra start" );

        AtomicInteger count = new AtomicInteger( 0 );
        Map<String, String> failed = new HashMap();
        Set<String> completed = new HashSet<>(); // to hold completed keys

        try
        {
            started = true;
            Set<String> prevCompleted = loadPrevCompleted();

            Set<TrackingKey> keySet = cacheRecord.getSealedTrackingKey();
            logger.info( "Get total records size: {}", keySet.size() );
            keySet.forEach( key -> {
                if ( !prevCompleted.contains( key.getId() ) )
                {
                    migrateForKey( key, count, completed, failed );
                }
            } );
            logger.info( "{}", count.get() );
            logger.info( "Migrate folo records from ISPN to cassandra done. Failed: {}\n{}", failed.size(), failed );
        }
        catch ( IOException e )
        {
            logger.error( "Migration failed", e );
        }
        finally
        {
            started = false;
            dumpResult( completed, failed );
        }

        return true;
    }

    private Set<String> loadPrevCompleted() throws IOException
    {
        Set<String> ret = new HashSet<>();
        File prevCompleted = getDataFile( COMPLETED_FILE );
        if ( prevCompleted.exists() )
        {
            try (InputStream is = new FileInputStream( prevCompleted ))
            {
                ret.addAll( IOUtils.readLines( is ) );
            }
        }
        logger.info( "Load prev completed, size: {}", ret.size() );
        return ret;
    }

    private void dumpResult( Set<String> completed, Map<String, String> failed )
    {
        // append
        try (OutputStream os = new FileOutputStream( getDataFile( COMPLETED_FILE ), true ))
        {
            IOUtils.writeLines( completed, LINE_SEPARATOR, os );
        }
        catch ( IOException e )
        {
            logger.error( "Failed to dump completed", e );
        }

        // override
        try (OutputStream os = new FileOutputStream( getDataFile( FAILED_FILE ) ))
        {
            IOUtils.writeLines( failed.keySet(), LINE_SEPARATOR, os );
        }
        catch ( IOException e )
        {
            logger.error( "Failed to dump failed", e );
        }
    }

    private File getDataFile( String path )
    {
        return new File( dataFileConfiguration.getDataBasedir(), path );
    }

    private void migrateForKey( TrackingKey key, AtomicInteger count, Set<String> completed,
                                Map<String, String> failed )
    {
        try
        {
            TrackedContent item = cacheRecord.get( key );
            if ( item != null )
            {
                // some (318) entries missing TrackingKey in download/upload TrackedContentEntry due to se/deserialization problem.
                amendTrackingKey( item );

                dbRecord.addSealedRecord( item );
                int index = count.incrementAndGet();
                if ( index % 10 == 0 )
                {
                    logger.info( "{}", index ); // print some log to show the progress
                }
                completed.add( key.getId() );
            }
            else
            {
                logger.warn( "Folo content missing, key: {}", key );
                failed.put( key.getId(), "content missing" );
            }
        }
        catch ( Exception e )
        {
            logger.error( "Folo content migrate failed, key: " + key, e );
            failed.put( key.getId(), e.toString() );
        }
    }

    private void amendTrackingKey( TrackedContent item )
    {
        TrackingKey key = item.getKey();
        Set<TrackedContentEntry> uploads = item.getUploads();
        if ( uploads != null )
        {
            uploads.forEach( up -> {
                if ( up.getTrackingKey() == null )
                {
                    up.setTrackingKey( key );
                }
            } );
        }
        Set<TrackedContentEntry> downloads = item.getDownloads();
        if ( downloads != null )
        {
            downloads.forEach( down -> {
                if ( down.getTrackingKey() == null )
                {
                    down.setTrackingKey( key );
                }
            } );
        }
    }
}
