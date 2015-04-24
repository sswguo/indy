/*******************************************************************************
 * Copyright (c) 2014 Red Hat, Inc..
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 * 
 * Contributors:
 *     Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package org.commonjava.aprox.dotmaven.inject;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Produces;
import javax.inject.Inject;

import net.sf.webdav.impl.ActivationMimeTyper;
import net.sf.webdav.impl.SimpleWebdavConfig;
import net.sf.webdav.spi.IMimeTyper;
import net.sf.webdav.spi.WebdavConfig;

import org.commonjava.aprox.dotmaven.store.DotMavenStore;
import org.commonjava.aprox.dotmaven.webctl.DotMavenService;
import org.commonjava.aprox.dotmaven.webctl.RequestInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
public class DotMavenProvider
{

    private final Logger logger = LoggerFactory.getLogger( getClass() );

    private WebdavConfig config;

    private DotMavenService service;

    @Inject
    private RequestInfo requestInfo;

    @Inject
    private DotMavenStore store;

    private IMimeTyper mimeTyper;

    @Produces
    public DotMavenService getService()
    {
        if ( service == null )
        {
            service = new DotMavenService( getConfig(), store, getMimeTyper(), requestInfo );
        }

        logger.info( "Returning WebDAV service: {}", service );
        return service;
    }

    @Produces
    public synchronized IMimeTyper getMimeTyper()
    {
        if ( mimeTyper == null )
        {
            mimeTyper = new ActivationMimeTyper();
        }

        return mimeTyper;
    }

    @Produces
    public synchronized WebdavConfig getConfig()
    {
        if ( config == null )
        {
            config = new SimpleWebdavConfig().withLazyFolderCreationOnPut()
                                             .withoutOmitContentLengthHeader();
        }

        return config;
    }
}
