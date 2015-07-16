/**
 * Copyright (C) 2011 Red Hat, Inc. (jdcasey@commonjava.org)
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
package org.commonjava.aprox.conf;

import java.io.File;

import org.commonjava.web.config.ConfigurationException;

/**
 * Describes a facility for loading all the configurations related to AProx, only one of which is {@link AproxConfiguration}.
 */
public interface AproxConfigFactory
{
    String APROX_HOME_PROP = "aprox.home";

    String CONFIG_PATH_PROP = "aprox.config";

    String CONFIG_DIR_PROP = CONFIG_PATH_PROP + ".dir";

    String DEFAULT_CONFIG_DIR = "/etc/aprox";

    String DEFAULT_CONFIG_PATH = DEFAULT_CONFIG_DIR + "/main.conf";

    /**
     * Return the configuration instance corresponding to the given class.
     */
    <T> T getConfiguration( Class<T> configCls )
        throws ConfigurationException;

    /**
     * Read all configurations and apply them to the different configuration-class instances available.
     * <br/><b>NOTE:</b>If the main.conf doesn't exist, {@link #writeDefaultConfigs(File)} will be called.
     * 
     * @param config Most commonly, a path to a configuration file.
     */
    void load( String config )
        throws ConfigurationException;

    /**
     * Query all configuration modules for their default configuration files and content, and write them to the specified configuration directory
     * structure. The given directory is equivalent to ${aprox.home}/etc/aprox, and configuration modules are allowed to return relative paths that
     * include a subdirectory (like conf.d/foo.conf).
     * 
     * @param dir The directory into which default configurations should be written.
     * @throws ConfigurationException
     */
    void writeDefaultConfigs( final File dir )
        throws ConfigurationException;

}
