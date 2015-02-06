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
package org.openrdf.server.object.repository;

import info.aduna.net.ParsedURI;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.openrdf.OpenRDFException;
import org.openrdf.model.Statement;
import org.openrdf.model.ValueFactory;
import org.openrdf.model.vocabulary.RDF;
import org.openrdf.query.BooleanQuery;
import org.openrdf.query.GraphQuery;
import org.openrdf.query.Query;
import org.openrdf.query.QueryLanguage;
import org.openrdf.query.TupleQuery;
import org.openrdf.query.resultio.text.tsv.SPARQLResultsTSVWriter;
import org.openrdf.repository.Repository;
import org.openrdf.repository.RepositoryConnection;
import org.openrdf.repository.RepositoryException;
import org.openrdf.repository.RepositoryResult;
import org.openrdf.repository.base.RepositoryWrapper;
import org.openrdf.repository.config.RepositoryConfigException;
import org.openrdf.repository.object.ObjectConnection;
import org.openrdf.repository.object.ObjectRepository;
import org.openrdf.repository.object.config.ObjectRepositoryConfig;
import org.openrdf.repository.object.config.ObjectRepositoryFactory;
import org.openrdf.repository.object.exceptions.ObjectStoreConfigException;
import org.openrdf.rio.ParserConfig;
import org.openrdf.rio.helpers.BasicParserSettings;
import org.openrdf.rio.turtle.TurtleWriter;
import org.openrdf.server.object.io.ArrangedWriter;
import org.openrdf.server.object.util.URLUtil;
import org.openrdf.store.blob.BlobStore;
import org.openrdf.store.blob.file.FileBlobStoreProvider;
import org.slf4j.LoggerFactory;

public class CalliRepository extends RepositoryWrapper implements CalliRepositoryMXBean {

	private static final String SLASH_ORIGIN = "/types/Origin";

	public static String getCallimachusWebapp(String url, RepositoryConnection con)
			throws RepositoryException {
		ParsedURI parsed = new ParsedURI(url + "/");
		String root = parsed.getScheme() + "://" + parsed.getAuthority() + "/";
		ValueFactory vf = con.getValueFactory();
		RepositoryResult<Statement> stmts;
		stmts = con
				.getStatements(vf.createURI(root), RDF.TYPE, null, false);
		try {
			while (stmts.hasNext()) {
				String type = stmts.next().getObject().stringValue();
				if (type.startsWith(root) && type.endsWith(SLASH_ORIGIN)) {
					int end = type.length() - SLASH_ORIGIN.length();
					return type.substring(0, end + 1);
				}
			}
			return null;
		} finally {
			stmts.close();
		}
	}

	private final org.slf4j.Logger logger = LoggerFactory.getLogger(CalliRepository.class);
	private final ObjectRepository object;
	private DatasourceManager datasources;
	private ParserConfig parserConfig;

	public CalliRepository(Repository repository, File dataDir)
			throws RepositoryConfigException, RepositoryException,
			IOException {
		assert repository != null;
		object = createObjectRepository(dataDir, repository);
		RepositoryWrapper wrapper = object;
		while (wrapper.getDelegate() instanceof RepositoryWrapper) {
			wrapper = (RepositoryWrapper) wrapper.getDelegate();
		}
		setDelegate(object);
		parserConfig = new ParserConfig();
		parserConfig.set(BasicParserSettings.PRESERVE_BNODE_IDS, true);
	}

	public DatasourceManager getDatasourceManager() {
		return datasources;
	}

	public void setDatasourceManager(DatasourceManager datasources) {
		this.datasources = datasources;
	}

	@Override
	public ObjectRepository getDelegate() {
		return object;
	}

	public int getMaxQueryTime() {
		return object.getMaxQueryTime();
	}

	public void setMaxQueryTime(int maxQueryTime) {
		object.setMaxQueryTime(maxQueryTime);
	}

	public boolean isIncludeInferred() {
		return object.isIncludeInferred();
	}

	public void setIncludeInferred(boolean includeInferred) {
		object.setIncludeInferred(includeInferred);
	}

	public BlobStore getBlobStore() throws ObjectStoreConfigException {
		return object.getBlobStore();
	}

	public void setBlobStore(BlobStore store) {
		object.setBlobStore(store);
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
		ObjectConnection conn = getConnection();
		try {
			return conn.getBlobObject(uri).getCharContent(true).toString();
		} finally {
			conn.close();
		}
	}

	public void storeBlob(String uri, String content) throws OpenRDFException, IOException {
		ObjectConnection conn = getConnection();
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

	public ObjectConnection getConnection() throws RepositoryException {
		ObjectConnection con = object.getConnection();
		con.setParserConfig(parserConfig);
		return con;
	}

	/**
	 * Resolves the relative path to the callimachus webapp context installed at
	 * the origin.
	 * 
	 * @param origin
	 *            scheme and authority
	 * @param path
	 *            relative path from the Callimachus webapp context
	 * @return absolute URL of the root + webapp context + path (or null)
	 */
	public String getCallimachusUrl(String origin, String path)
			throws OpenRDFException {
		String webapp = getCallimachusWebapp(origin);
		if (webapp == null)
			return null;
		return URLUtil.resolve(path, webapp);
	}

	/**
	 * Locates the location of the Callimachus webapp folder if present in same
	 * origin, given the root folder.
	 * 
	 * @param root
	 *            home folder, absolute URL with '/' as the path
	 * @return folder of the Callimachus webapp (or null)
	 * @throws OpenRDFException
	 */
	public String getCallimachusWebapp(String url) throws OpenRDFException {
		RepositoryConnection con = this.getConnection();
		try {
			return getCallimachusWebapp(url, con);
		} finally {
			con.close();
		}
	}

	private ObjectRepository createObjectRepository(File dataDir,
			Repository repository) throws RepositoryConfigException,
			RepositoryException, IOException {
		if (repository instanceof ObjectRepository)
			return (ObjectRepository) repository;
		// AdviceService used in ObjectRepository#compileSchema
		// uses java.util.ServiceLoader in a non-thread safe way
		synchronized (CalliRepository.class) {
			ObjectRepositoryFactory factory = new ObjectRepositoryFactory();
			ObjectRepositoryConfig config = factory.getConfig();
			File wwwDir = new File(dataDir, "www");
			File blobDir = new File(dataDir, "blob");
			if (wwwDir.isDirectory() && !blobDir.isDirectory()) {
				config.setBlobStore(wwwDir.toURI().toString());
				Map<String, String> map = new HashMap<String, String>();
				map.put("provider", FileBlobStoreProvider.class.getName());
				config.setBlobStoreParameters(map);
			} else {
				config.setBlobStore(blobDir.toURI().toString());
			}
			return factory.createRepository(config, repository);
		}
	}

	private void setHandlerLevel(Logger logger, Level level) {
		if (logger.getParent() != null) {
			setHandlerLevel(logger.getParent(), level);
		}
		Handler[] handlers = logger.getHandlers();
		if (handlers != null) {
			for (Handler handler : handlers) {
				if (handler.getLevel().intValue() > level.intValue()) {
					handler.setLevel(level);
				}
			}
		}
	}

}
