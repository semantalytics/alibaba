/*
 * Copyright (c) 2011, 3 Round Stones Inc. Some rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * - Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 * - Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution. 
 * - Neither the name of the openrdf.org nor the names of its contributors may
 *   be used to endorse or promote products derived from this software without
 *   specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * 
 */
package org.openrdf.sail.keyword;

import info.aduna.iteration.CloseableIteration;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

import org.openrdf.model.Resource;
import org.openrdf.model.Statement;
import org.openrdf.model.URI;
import org.openrdf.model.ValueFactory;
import org.openrdf.model.impl.ValueFactoryImpl;
import org.openrdf.sail.Sail;
import org.openrdf.sail.SailConnection;
import org.openrdf.sail.SailException;
import org.openrdf.sail.helpers.SailWrapper;
import org.openrdf.sail.inferencer.InferencerConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Add keyword:phone property of resource's label soundex. Label properties to
 * index are read from META-INF/org.openrdf.sail.keyword.property. The index
 * property and graph are configurable.
 * 
 * @author James Leigh
 * 
 */
public class KeywordSail extends SailWrapper {
	private static final String SETTING_PROPERTIES = "org.openrdf.sail.keyword.properties";
	private static final String PHONE_URI = "http://www.openrdf.org/rdf/2011/keyword#phone";
	private final Logger logger = LoggerFactory.getLogger(KeywordSail.class);
	private URI property = ValueFactoryImpl.getInstance().createURI(PHONE_URI);
	private URI graph = null;
	private Set<URI> labels;
	private final PhoneHelper helper = PhoneHelperFactory.newInstance()
			.createPhoneHelper();

	public KeywordSail() {
		super();
	}

	public KeywordSail(Sail baseSail) {
		super(baseSail);
	}

	public String toString() {
		return getBaseSail().toString();
	}

	public URI getPhoneProperty() {
		return property;
	}

	/**
	 * RDF predicate to index resources with.
	 */
	public void setPhoneProperty(URI property) {
		assert property != null;
		this.property = property;
	}

	public URI getPhoneGraph() {
		return graph;
	}

	/**
	 * Where to index soundex phone properties.
	 */
	public void setPhoneGraph(URI graph) {
		this.graph = graph;
	}

	@Override
	public void initialize() throws SailException {
		super.initialize();
		ValueFactory vf = getValueFactory();
		property = vf.createURI(property.stringValue());
		if (graph != null) {
			graph = vf.createURI(graph.stringValue());
		}
		labels = new HashSet<URI>(helper.getProperties().size());
		for (String uri : helper.getProperties()) {
			labels.add(vf.createURI(uri));
		}
		try {
			File dir = getDataDir();
			if (dir != null) {
				Properties properties = loadSettings(dir);
				if (!isSameSettings(properties)) {
					logger.info("Reindexing keywords in {}", this);
					clear(properties);
					reindex();
				}
				saveSettings(dir);
			}
		} catch (IOException e) {
			throw new SailException(e);
		}
	}

	@Override
	public KeywordConnection getConnection() throws SailException {
		return new KeywordConnection(this, super.getConnection(), helper);
	}

	protected boolean isIndexedProperty(URI property) {
		return labels.contains(property);
	}

	private Properties loadSettings(File dir) throws FileNotFoundException,
			IOException {
		Properties properties = new Properties();
		File file = new File(dir, SETTING_PROPERTIES);
		if (file.exists()) {
			FileInputStream in = new FileInputStream(file);
			try {
				properties.load(in);
			} finally {
				in.close();
			}
		}
		return properties;
	}

	private boolean isSameSettings(Properties properties) {
		if (!Integer.toHexString(helper.hashCode()).equals(
				properties.getProperty("phone")))
			return false;
		if (!property.stringValue().equals(properties.getProperty("property")))
			return false;
		if (graph == null)
			return properties.getProperty("graph") == null;
		return graph.stringValue().equals(properties.getProperty("graph"));
	}

	private void clear(Properties properties) throws SailException {
		ValueFactory vf = getValueFactory();
		String property = properties.getProperty("property");
		if (property != null) {
			URI pred = vf.createURI(property);
			String graph = properties.getProperty("graph");
			Resource context = graph == null ? null : vf.createURI(graph);
			SailConnection con = super.getConnection();
			try {
				if (con instanceof InferencerConnection) {
					InferencerConnection icon = (InferencerConnection) con;
					icon.removeInferredStatement(null, pred, null, context);
				} else {
					con.removeStatements(null, pred, null, context);
				}
				con.commit();
			} finally {
				con.close();
			}
		}
	}

	private void reindex() throws SailException {
		KeywordConnection con = getConnection();
		try {
			for (URI pred : labels) {
				CloseableIteration<? extends Statement, SailException> stmts;
				stmts = con.getStatements(null, pred, null, false);
				try {
					while (stmts.hasNext()) {
						Statement st = stmts.next();
						con.index(st.getSubject(), st.getObject());
					}
				} finally {
					stmts.close();
				}
			}
			con.commit();
		} finally {
			con.close();
		}
	}

	private void saveSettings(File dir) throws IOException {
		Properties properties = new Properties();
		String code = Integer.toHexString(helper.hashCode());
		properties.setProperty("phone", code);
		properties.setProperty("property", property.stringValue());
		if (graph == null) {
			properties.remove("graph");
		} else {
			properties.setProperty("graph", graph.stringValue());
		}
		File file = new File(dir, SETTING_PROPERTIES);
		FileOutputStream out = new FileOutputStream(file);
		try {
			properties.store(out, this.toString());
		} finally {
			out.close();
		}
	}

}
