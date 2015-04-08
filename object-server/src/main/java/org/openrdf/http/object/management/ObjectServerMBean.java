/*
 * Copyright (c) 2013 3 Round Stones Inc., Some Rights Reserved
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package org.openrdf.http.object.management;

import java.io.IOException;

import org.openrdf.OpenRDFException;

public interface ObjectServerMBean {

	String getServerName() throws IOException;

	void setServerName(String name) throws IOException;

	String getPorts() throws IOException;

	void setPorts(String ports) throws IOException;

	String getSSLPorts() throws IOException;

	void setSSLPorts(String ports) throws IOException;

	int getTimeout();

	void setTimeout(int timeout);

	boolean isShutDown();

	boolean isRunning();

	boolean isStartingInProgress();

	boolean isStoppingInProgress();

	boolean isCompilingInProgress();

	String getStatus();

	void poke();

	void resetCache();

	void recompileSchema() throws IOException, OpenRDFException;

	void resetConnections() throws IOException;

	ConnectionBean[] getConnections();

	void connectionDumpToFile(String outputFile) throws IOException;

	String addRepository(String base, String config) throws OpenRDFException,
			IOException;

	boolean removeRepository(String id) throws OpenRDFException;

	String[] getRepositoryIDs() throws OpenRDFException;

	String[] getRepositoryPrefixes(String id) throws OpenRDFException;

	void addRepositoryPrefix(String id, String prefix) throws OpenRDFException;

	void setRepositoryPrefixes(String id, String[] prefixes) throws OpenRDFException;

	void init() throws IOException, OpenRDFException;

	void start() throws IOException, OpenRDFException;

	void stop() throws IOException, OpenRDFException;

	void destroy() throws OpenRDFException, IOException;

	void restart() throws IOException, OpenRDFException;

}
