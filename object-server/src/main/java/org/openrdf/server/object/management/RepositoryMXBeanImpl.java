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
package org.openrdf.server.object.management;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;

import org.openrdf.OpenRDFException;
import org.openrdf.query.BooleanQuery;
import org.openrdf.query.GraphQuery;
import org.openrdf.query.Query;
import org.openrdf.query.QueryLanguage;
import org.openrdf.query.TupleQuery;
import org.openrdf.query.resultio.text.tsv.SPARQLResultsTSVWriter;
import org.openrdf.repository.Repository;
import org.openrdf.repository.RepositoryConnection;
import org.openrdf.repository.RepositoryException;
import org.openrdf.repository.contextaware.ContextAwareRepository;
import org.openrdf.repository.object.ObjectConnection;
import org.openrdf.repository.object.ObjectRepository;
import org.openrdf.repository.object.exceptions.ObjectStoreConfigException;
import org.openrdf.repository.sail.config.RepositoryResolver;
import org.openrdf.rio.ParserConfig;
import org.openrdf.rio.helpers.BasicParserSettings;
import org.openrdf.rio.turtle.TurtleWriter;
import org.openrdf.server.object.io.ArrangedWriter;
import org.openrdf.store.blob.BlobStore;
import org.slf4j.LoggerFactory;

public class RepositoryMXBeanImpl implements RepositoryMXBean {

	private final org.slf4j.Logger logger = LoggerFactory.getLogger(RepositoryMXBeanImpl.class);
	private final ParserConfig parserConfig = new ParserConfig();
	private final RepositoryResolver resolver;
	private final String id;

	public RepositoryMXBeanImpl(RepositoryResolver resolver, String id) {
		this.resolver = resolver;
		this.id = id;
		parserConfig.set(BasicParserSettings.PRESERVE_BNODE_IDS, true);
	}

	public int getMaxQueryTime() throws OpenRDFException {
		return getContextAwareRepository().getMaxQueryTime();
	}

	public void setMaxQueryTime(int maxQueryTime) throws OpenRDFException {
		getContextAwareRepository().setMaxQueryTime(maxQueryTime);
	}

	public boolean isIncludeInferred() throws OpenRDFException {
		return getContextAwareRepository().isIncludeInferred();
	}

	public void setIncludeInferred(boolean includeInferred) throws OpenRDFException {
		getContextAwareRepository().setIncludeInferred(includeInferred);
	}

	public BlobStore getBlobStore() throws OpenRDFException, ObjectStoreConfigException {
		return getObjectRepository().getBlobStore();
	}

	public void setBlobStore(BlobStore store) throws OpenRDFException {
		getObjectRepository().setBlobStore(store);
	}

	public String[] sparqlQuery(String query) throws OpenRDFException, IOException {
		RepositoryConnection conn = this.getConnection();
		try {
			Query qry = conn.prepareQuery(QueryLanguage.SPARQL, query);
			if (qry instanceof TupleQuery) {
				ByteArrayOutputStream out = new ByteArrayOutputStream();
				SPARQLResultsTSVWriter writer = new SPARQLResultsTSVWriter(out);
				((TupleQuery) qry).evaluate(writer);
				return new String(out.toByteArray(), "UTF-8").split("\r?\n");
			} else if (qry instanceof BooleanQuery) {
				return new String[]{String.valueOf(((BooleanQuery) qry).evaluate())};
			} else if (qry instanceof GraphQuery) {
				StringWriter string = new StringWriter(65536);
				TurtleWriter writer = new TurtleWriter(string);
				((GraphQuery) qry).evaluate(new ArrangedWriter(writer));
				return string.toString().split("(?<=\\.)\r?\n");
			} else {
				throw new RepositoryException("Unknown query type: " + qry.getClass().getSimpleName());
			}
		} finally {
			conn.close();
		}
	}

	public void sparqlUpdate(String update) throws OpenRDFException, IOException {
		RepositoryConnection conn = this.getConnection();
		try {
			logger.info(update);
			conn.prepareUpdate(QueryLanguage.SPARQL, update).execute();
		} finally {
			conn.close();
		}
	}

	public String getBlob(String uri) throws OpenRDFException, IOException {
		ObjectConnection conn = getObjectConnection();
		try {
			return conn.getBlobObject(uri).getCharContent(true).toString();
		} finally {
			conn.close();
		}
	}

	public void storeBlob(String uri, String content) throws OpenRDFException, IOException {
		ObjectConnection conn = getObjectConnection();
		try {
			logger.warn("Replacing {}", uri);
			Writer writer = conn.getBlobObject(uri).openWriter();
			try {
				writer.write(content);
			} finally {
				writer.close();
			}
		} finally {
			conn.close();
		}
	}

	private RepositoryConnection getConnection() throws OpenRDFException {
		RepositoryConnection con = getRepository().getConnection();
		con.setParserConfig(parserConfig);
		return con;
	}

	private ObjectConnection getObjectConnection() throws OpenRDFException {
		ObjectConnection con = getObjectRepository().getConnection();
		con.setParserConfig(parserConfig);
		return con;
	}

	private ObjectRepository getObjectRepository() throws OpenRDFException {
		return (ObjectRepository) getRepository();
	}

	private ContextAwareRepository getContextAwareRepository() throws OpenRDFException {
		return (ContextAwareRepository) getRepository();
	}

	private Repository getRepository() throws OpenRDFException {
		return resolver.getRepository(id);
	}

}
