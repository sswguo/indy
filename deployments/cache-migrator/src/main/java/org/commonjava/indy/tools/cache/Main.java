/**
 * Copyright (C) 2011-2018 Red Hat, Inc. (https://github.com/Commonjava/indy)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.commonjava.indy.tools.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.apache.commons.io.FileUtils;
import org.commonjava.indy.IndyWorkflowException;
import org.commonjava.indy.action.IndyLifecycleException;
import org.commonjava.indy.boot.IndyBootException;
import org.commonjava.indy.folo.data.FoloFiler;
import org.commonjava.indy.folo.model.TrackedContent;
import org.commonjava.indy.folo.model.TrackingKey;
import org.commonjava.indy.model.core.io.IndyObjectMapper;
import org.commonjava.indy.subsys.infinispan.CacheHandle;
import org.eclipse.jgit.errors.LargeObjectException;
import org.infinispan.commons.api.BasicCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import static org.commonjava.indy.boot.BootInterface.ERR_CANT_INIT_BOOTER;
import static org.commonjava.indy.boot.BootInterface.ERR_CANT_PARSE_ARGS;
import static org.commonjava.indy.folo.FoloUtils.zipTrackedContent;

public class Main
{
    private final Logger logger = LoggerFactory.getLogger( getClass() );

    private SimpleCacheProducer producer;

    private ObjectMapper objectMapper;

    public static void main( String[] args )
    {
        Thread.currentThread()
              .setUncaughtExceptionHandler( ( thread, error ) -> {
                  if ( error instanceof InvocationTargetException )
                  {
                      final InvocationTargetException ite = (InvocationTargetException) error;
                      System.err.println( "In: " + thread.getName() + "(" + thread.getId()
                                                  + "), caught InvocationTargetException:" );
                      ite.getTargetException()
                         .printStackTrace();

                      System.err.println( "...via:" );
                      error.printStackTrace();
                  }
                  else
                  {
                      System.err.println( "In: " + thread.getName() + "(" + thread.getId() + ") Uncaught error:" );
                      error.printStackTrace();
                  }
              } );

        MigrationOptions options = new MigrationOptions();
        try
        {
            if ( options.parseArgs( args ) )
            {
                try
                {
                    int result = new Main().run( options );
                    if ( result != 0 )
                    {
                        System.exit( result );
                    }
                }
                catch ( final IndyBootException e )
                {
                    System.err.printf( "ERROR INITIALIZING BOOTER: %s", e.getMessage() );
                    System.exit( ERR_CANT_INIT_BOOTER );
                }
            }
        }
        catch ( final IndyBootException e )
        {
            System.err.printf( "ERROR: %s", e.getMessage() );
            System.exit( ERR_CANT_PARSE_ARGS );
        }
    }

    private int run( final MigrationOptions options )
            throws IndyBootException
    {
        try
        {
            File inXml = options.getInfinispanXml();
            if ( inXml != null )
            {
                File outXmlDir = new File( System.getProperty("java.io.tmpdir", "/tmp"), "infinispan-config-" + System.currentTimeMillis());
                if ( !outXmlDir.isDirectory() && !outXmlDir.mkdirs() )
                {
                    throw new IndyBootException(
                            "Failed to create temporary direcory for infinispan configuration loading" );
                }

                File outXml = new File( outXmlDir, "infinispan.xml" );
                FileUtils.copyFile( inXml, outXml );

                Properties props = System.getProperties();

                props.setProperty( "indy.config.dir", outXmlDir.getAbsolutePath() );

                System.setProperties( props );
            }

            producer = new SimpleCacheProducer();
            objectMapper = new IndyObjectMapper(true);
            objectMapper.disable( SerializationFeature.INDENT_OUTPUT );

            CacheHandle<Object, Object> cache = producer.getCache( options.getCacheName() );

            //checkMissingRecords ( cache );
            //loadFromBakDir( cache );

            if ( MigrationCommand.dump == options.getMigrationCommand() )
            {

                if ( DataType.json == options.getDataType() )
                {
                    dumpJsonFile( cache,  options );
                }
                else
                {
                    dumpObjectFile( cache, options );
                }

            }
            else if ( MigrationCommand.export == options.getMigrationCommand() )
            {
                exportReport( cache, options );
            }
            else
            {
                if ( DataType.json == options.getDataType() )
                {
                    loadFromJsonFile( cache, options );
                }
                else
                {
                    loadFromObjectFile( cache, options );
                }

            }

        }
        catch ( final Throwable e )
        {
            if ( e instanceof IndyBootException )
                throw (IndyBootException)e;

            logger.error( "Failed to initialize Booter: " + e.getMessage(), e );
            return ERR_CANT_INIT_BOOTER;
        }
        finally
        {
            try
            {
                producer.stop();
            }
            catch ( final IndyLifecycleException e )
            {
                logger.error( "Failed to stop cache subsystem: " + e.getMessage(), e );
            }
        }

        return 0;
    }

    /**
     *
     * @param cache
     * @param options
     * @throws IndyBootException
     */
    private void exportReport( CacheHandle<Object, Object> cache, MigrationOptions options ) throws IndyBootException
    {
        Set<TrackingKey> sealed = new HashSet<>(  );
        cache.executeCache( (c) -> {
            c.forEach( (k, v) -> {
                sealed.add( (TrackingKey) k );
            } );
            return true;
        } );

        System.out.println( sealed.size() );

        try
        {
            File file = options.getDataFile();
            if ( file.exists() )
            {
                file.delete();
            }
            file.getParentFile().mkdirs(); // make dirs if not exist

            zipTrackedContent( file, sealed, cache );
        }
        catch ( IOException e )
        {
            throw new IndyBootException( " Failed to export sealed report. ", e );
        }
    }

    private void checkMissingRecords( CacheHandle<Object, Object> cache )
    {
        //String csvFile = "/Users/wguo/Documents/Indy/folo_debug/missing-records.csv";
        //String csvFile = "/Users/wguo/Documents/Indy/folo_debug/missing-tracking-reports-even-in-bak.csv";
        String csvFile = "/Users/wguo/Documents/Indy/folo_debug/missing-tracking-reports-1.csv";

        cache.executeCache( c -> {
            try ( BufferedReader br = new BufferedReader(new FileReader( csvFile)) )
            {
                int count = 0;
                String line = "";
                int missing = 0;
                while ((line = br.readLine()) != null) {
                    count ++;
                    String[] item = line.split(",");

                    //System.out.println("tracking key: " + item[1]);

                    if ( c.get( new TrackingKey( item[1] ) ) != null )
                    {

                    }
                    else
                    {
                        System.out.println(item[0] + "," + item[1]);
                        missing++;
                    }

                }
                System.out.println( "Checking total: " + count + "| missing:" + missing );
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
                return true;
            } );

    }

    private void loadFromBakDir( CacheHandle<Object, Object> cache )
    {
        try
        {
            String bak_dir = "/Users/wguo/stage/indy/var/lib/indy/data/folo/bak/sealed";
            List<File> filesInFolder = Files.walk( Paths.get( bak_dir ) )
                                            .filter( Files::isRegularFile )
                                            .map( Path::toFile )
                                            .collect( Collectors.toList() );
            logger.info( " Files: {} ", filesInFolder.size() );

            filesInFolder.stream().forEach( file -> {
                try( ObjectInputStream in = new ObjectInputStream( new FileInputStream( file ) ) )
                {
                    System.out.println( "File: " + file.getName() );
                    Object k = new TrackingKey( file.getName() );
                    Object v = in.readObject();
                    cache.executeCache( (c) -> {
                        c.putAsync( k, v );
                        return true;
                    } );
                }
                catch ( IOException e )
                {
                    e.printStackTrace();
                }
                catch ( ClassNotFoundException e )
                {
                    e.printStackTrace();
                }
            } );

        }
        catch ( IOException e )
        {
            logger.error( "Read file error.", e );
        }
    }

    private void loadFromJsonFile( CacheHandle<Object, Object> cache, MigrationOptions options ) throws IndyBootException
    {
        AtomicReference<Throwable> error = new AtomicReference<>();
        try (BufferedReader in = new BufferedReader( new InputStreamReader(
                        new FileInputStream( options.getDataFile() )  )))
        {
            cache.executeCache( (c)->{
                try
                {
                    String key;
                    int count = 0;
                    int existing = 0;
                    while ( (key = in.readLine()) != null )
                    {
                        try
                        {
                            Object k = objectMapper.readValue( key, TrackingKey.class );
                            Object v = objectMapper.readValue( in.readLine(), TrackedContent.class );
                            if ( c.get( k ) == null )
                            {
                                c.putAsync( k, v );
                            }
                            else
                            {
                                System.out.println( ( (TrackingKey) k ).getId() );
                                existing++;
                                c.remove( k, v );
                            }
                            count++;
                        }
                        catch ( Exception e )
                        {
                            logger.error( "Failed to read entry key: {}", key, e );
                            error.set( e );
                        }
                    }
                    logger.info( "Load entries: {}", count );
                    logger.info( "Existing entries: {}", existing );
                    logger.info( "New entries: {}", c.size());
                }
                catch ( Exception e )
                {
                    logger.error( "Failed to read data file header.", e );
                    error.set( e );
                }
                return true;
            } );
        }
        catch ( IOException e )
        {
            error.set( e );
        }

        if ( error.get() != null )
        {
            throw new IndyBootException( "Failed to read data from file: " + options.getDataFile(), error.get() );
        }
    }

    private void loadFromObjectFile( CacheHandle<Object, Object> cache, MigrationOptions options ) throws IndyBootException
    {
        AtomicReference<Throwable> error = new AtomicReference<>();
        try (ObjectInputStream in = new ObjectInputStream(
                        new GZIPInputStream( new FileInputStream( options.getDataFile() ) ) ))
        {
            cache.executeCache( (c)->{
                try
                {
                    long records = in.readLong();

                    for(long i=0; i<records; i++)
                    {
                        try
                        {
                            Object k = in.readObject();
                            Object v = in.readObject();

                            c.putAsync( k, v );
                        }
                        catch ( Exception e )
                        {
                            logger.error( "Failed to read entry at index: " + i, e );
                            error.set( e );
                        }
                    }
                    logger.info( "Load {} complete, size: {}", options.getCacheName(), records );
                }
                catch ( IOException e )
                {
                    logger.error( "Failed to read data file header.", e );
                    error.set( e );
                }
                return true;
            } );
        }
        catch ( IOException e )
        {
            error.set( e );
        }

        if ( error.get() != null )
        {
            throw new IndyBootException( "Failed to read data from file: " + options.getDataFile(), error.get() );
        }
    }

    private void dumpObjectFile( CacheHandle<Object, Object> cache, MigrationOptions options ) throws IndyBootException
    {

        AtomicReference<Throwable> error = new AtomicReference<>();
        try (ObjectOutputStream out = new ObjectOutputStream( new GZIPOutputStream( new FileOutputStream( options.getDataFile() ) )))
        {
            cache.executeCache( ( c ) -> {
                try
                {
                    out.writeLong( c.size() );
                }
                catch ( IOException e )
                {
                    logger.error( "Failed to write data file header.", e );
                    error.set( e );
                }

                if ( error.get() == null )
                {
                    c.forEach( ( k, v ) -> {
                        if ( error.get() == null )
                        {
                            try
                            {
                                out.writeObject( k );
                                out.writeObject( v );
                            }
                            catch ( IOException e )
                            {
                                logger.error( "Failed to write entry with key: " + k, e );
                                error.set( e );
                            }
                        }
                    });
                }

                return true;
            } );
        }
        catch ( IOException e )
        {
            error.set( e );
        }

        if ( error.get() != null )
        {
            throw new IndyBootException( "Failed to write data to file: " + options.getDataFile(), error.get() );
        }
    }

    private void dumpJsonFile( CacheHandle<Object, Object> cache,  MigrationOptions options ) throws IndyBootException
    {
        AtomicReference<Throwable> error = new AtomicReference<>();
        try (BufferedOutputStream out =  new BufferedOutputStream( new FileOutputStream( options.getDataFile() )   ))
        {
            String lineSeparator = System.getProperty("line.separator");

            cache.executeCache( ( c ) -> {

                if ( error.get() == null )
                {
                    c.forEach( ( k, v ) -> {
                        if ( error.get() == null )
                        {
                            try
                            {
                                out.write( objectMapper.writeValueAsBytes( k ) );
                                out.write( lineSeparator.getBytes() );
                                out.write( objectMapper.writeValueAsBytes( v ) );
                                out.write( lineSeparator.getBytes() );
                                out.flush();
                            }
                            catch ( IOException e )
                            {
                                logger.error( "Failed to write entry with key: " + k, e );
                                error.set( e );
                            }
                        }
                    });
                }

                return true;
            } );
        }
        catch ( IOException e )
        {
            error.set( e );
        }

        if ( error.get() != null )
        {
            throw new IndyBootException( "Failed to write data to file: " + options.getDataFile(), error.get() );
        }
    }
}
