/*******************************************************************************
 * Copyright 2011 John Casey
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/
package org.commonjava.aprox.core.data;

import static org.apache.commons.lang.StringUtils.join;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.apache.log4j.Level;
import org.commonjava.aprox.io.StorageProvider;
import org.commonjava.aprox.model.StoreKey;
import org.commonjava.aprox.model.StoreType;
import org.commonjava.util.logging.Log4jUtil;
import org.junit.BeforeClass;
import org.junit.Test;

public abstract class FilerTCK
{

    protected abstract StorageProvider getStorageProvider()
        throws Exception;

    @BeforeClass
    public static void setupLogging()
    {
        Log4jUtil.configure( Level.DEBUG );
    }

    @Test
    public void writeAndVerifyDirectory()
        throws Exception
    {
        final String content = "This is a test";

        final StoreKey key = new StoreKey( StoreType.deploy_point, "foo" );
        final String dir = "/path/to/my/";
        final String fname = dir + "file.txt";

        final StorageProvider provider = getStorageProvider();
        final OutputStream out = provider.openOutputStream( key, fname );
        out.write( content.getBytes( "UTF-8" ) );
        out.close();

        assertThat( provider.isDirectory( key, dir ), equalTo( true ) );
    }

    @Test
    public void writeAndListDirectory()
        throws Exception
    {
        final String content = "This is a test";

        final StoreKey key = new StoreKey( StoreType.deploy_point, "foo" );
        final String dir = "/path/to/my/";
        final String fname = dir + "file.txt";

        final StorageProvider provider = getStorageProvider();
        final OutputStream out = provider.openOutputStream( key, fname );
        out.write( content.getBytes( "UTF-8" ) );
        out.flush();
        out.close();

        // NOTE: This is NOT as tightly specified as I would like. 
        // We keep the listing assertions loose (greater-than instead of equals, 
        // contains instead of exact positional assertion) because the Infinispan
        // live testing has these spurious foo.txt.#0 files cropping up.
        //
        // I have no idea what they are, but I'm sick of fighting JBoss bugs for now.
        final Set<String> listing = new HashSet<String>( Arrays.asList( provider.list( key, dir ) ) );

        System.out.printf( "\n\nFile listing is:\n\n  %s\n\n\n", join( listing, "\n  " ) );

        assertThat( listing.size() > 0, equalTo( true ) );
        assertThat( listing.contains( "file.txt" ), equalTo( true ) );
    }

    @Test
    public void writeAndVerifyExistence()
        throws Exception
    {
        final String content = "This is a test";

        final StoreKey key = new StoreKey( StoreType.deploy_point, "foo" );
        final String fname = "/path/to/my/file.txt";

        final StorageProvider provider = getStorageProvider();
        final OutputStream out = provider.openOutputStream( key, fname );
        out.write( content.getBytes( "UTF-8" ) );
        out.close();

        assertThat( provider.exists( key, fname ), equalTo( true ) );
    }

    @Test
    public void writeDeleteAndVerifyNonExistence()
        throws Exception
    {
        final String content = "This is a test";

        final StoreKey key = new StoreKey( StoreType.deploy_point, "foo" );
        final String fname = "/path/to/my/file.txt";

        final StorageProvider provider = getStorageProvider();
        final OutputStream out = provider.openOutputStream( key, fname );
        out.write( content.getBytes( "UTF-8" ) );
        out.close();

        assertThat( provider.exists( key, fname ), equalTo( true ) );

        provider.delete( key, fname );

        assertThat( provider.exists( key, fname ), equalTo( false ) );
    }

    @Test
    public void writeAndReadFile()
        throws Exception
    {
        final String content = "This is a test";

        final StoreKey key = new StoreKey( StoreType.deploy_point, "foo" );
        final String fname = "/path/to/my/file.txt";

        final StorageProvider provider = getStorageProvider();
        final OutputStream out = provider.openOutputStream( key, fname );
        out.write( content.getBytes( "UTF-8" ) );
        out.close();

        final InputStream in = provider.openInputStream( key, fname );
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        int read = -1;
        final byte[] buf = new byte[512];
        while ( ( read = in.read( buf ) ) > -1 )
        {
            baos.write( buf, 0, read );
        }

        final String result = new String( baos.toByteArray(), "UTF-8" );

        assertThat( result, equalTo( content ) );
    }

    @Test
    public void writeCopyAndReadNewFile()
        throws Exception
    {
        final String content = "This is a test";

        final StoreKey key = new StoreKey( StoreType.deploy_point, "foo" );
        final String fname = "/path/to/my/file.txt";

        final StoreKey key2 = new StoreKey( StoreType.deploy_point, "bar" );

        final StorageProvider provider = getStorageProvider();
        final OutputStream out = provider.openOutputStream( key, fname );
        out.write( content.getBytes( "UTF-8" ) );
        out.close();

        provider.copy( key, fname, key2, fname );

        final InputStream in = provider.openInputStream( key2, fname );
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        int read = -1;
        final byte[] buf = new byte[512];
        while ( ( read = in.read( buf ) ) > -1 )
        {
            baos.write( buf, 0, read );
        }

        final String result = new String( baos.toByteArray(), "UTF-8" );

        assertThat( result, equalTo( content ) );
    }

}
