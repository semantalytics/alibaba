/*
 * Copyright (c) 2014 3 Round Stones Inc., Some Rights Reserved
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

public interface RepositoryMXBean {

	int getMaxQueryTime() throws OpenRDFException;

	void setMaxQueryTime(int maxQueryTime) throws OpenRDFException;

	boolean isIncludeInferred() throws OpenRDFException;

	void setIncludeInferred(boolean includeInferred) throws OpenRDFException;

	String[] sparqlQuery(String query) throws OpenRDFException, IOException;

	void sparqlUpdate(String update) throws OpenRDFException, IOException;

	String readCharacterBlob(String uri) throws OpenRDFException, IOException;

	byte[] readBinaryBlob(String uri) throws OpenRDFException, IOException;

	void storeCharacterBlob(String uri, String content) throws OpenRDFException, IOException;

	void storeBinaryBlob(String uri, byte[] content) throws OpenRDFException, IOException;

}
