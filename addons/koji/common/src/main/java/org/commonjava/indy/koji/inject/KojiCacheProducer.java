/**
 * Copyright (C) 2011-2020 Red Hat, Inc. (https://github.com/Commonjava/indy)
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
package org.commonjava.indy.koji.inject;

import org.apache.maven.artifact.repository.metadata.Metadata;
import org.commonjava.indy.subsys.infinispan.BasicCacheHandle;
import org.commonjava.indy.subsys.infinispan.CacheHandle;
import org.commonjava.indy.subsys.infinispan.CacheProducer;
import org.commonjava.atlas.maven.ident.ref.ProjectRef;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Produces;
import javax.inject.Inject;
import java.util.Date;

/**
 * Create ISPN caches necessary to support Koji metadata provider functions.
 */
@ApplicationScoped
public class KojiCacheProducer
{
    @Inject
    private CacheProducer cacheProducer;

    @KojiMavenVersionMetadataCache
    @Produces
    @ApplicationScoped
    public CacheHandle<ProjectRef, Metadata> versionMetadataCache()
    {
        return cacheProducer.getCache( "koji-maven-version-metadata" );
    }
}
