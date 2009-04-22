package org.openrdf.repository.sparql.query;

import java.io.IOException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpException;
import org.apache.commons.httpclient.HttpMethodBase;
import org.apache.commons.httpclient.methods.PostMethod;
import org.openrdf.model.Literal;
import org.openrdf.model.URI;
import org.openrdf.model.Value;
import org.openrdf.query.BindingSet;
import org.openrdf.query.Dataset;
import org.openrdf.query.Query;
import org.openrdf.query.QueryEvaluationException;
import org.openrdf.query.impl.DatasetImpl;
import org.openrdf.query.impl.MapBindingSet;

public abstract class SPARQLQuery implements Query {
	private static Executor executor = Executors.newCachedThreadPool();
	private HttpClient client;
	private String url;
	private String query;
	private Dataset dataset = new DatasetImpl();
	private MapBindingSet bindings = new MapBindingSet();

	public SPARQLQuery(HttpClient client, String url, String query) {
		this.url = url;
		this.query = query;
		this.client = client;
	}

	public BindingSet getBindings() {
		return bindings;
	}

	public Dataset getDataset() {
		return dataset;
	}

	public boolean getIncludeInferred() {
		return true;
	}

	public int getMaxQueryTime() {
		return 0;
	}

	public void removeBinding(String name) {
		bindings.removeBinding(name);
	}

	public void setBinding(String name, Value value) {
		assert value instanceof Literal || value instanceof URI;
		bindings.addBinding(name, value);
	}

	public void setDataset(Dataset dataset) {
		this.dataset = dataset;
	}

	public void setIncludeInferred(boolean inf) {
		if (!inf) {
			throw new UnsupportedOperationException();
		}
	}

	public void setMaxQueryTime(int maxQueryTime) {
		throw new UnsupportedOperationException();
	}

	public String getUrl() {
		return url;
	}

	protected HttpMethodBase getResponse() throws HttpException, IOException,
			QueryEvaluationException {
		PostMethod post = new PostMethod(url);
		post.addParameter("query", getQueryString());
		for (URI graph : dataset.getDefaultGraphs()) {
			post.addParameter("default-graph-uri", graph.stringValue());
		}
		for (URI graph : dataset.getNamedGraphs()) {
			post.addParameter("named-graph-uri", graph.stringValue());
		}
		post.addRequestHeader("Accept", getAccept());
		boolean completed = false;
		try {
			if (client.executeMethod(post) >= 400) {
				throw new QueryEvaluationException(post
						.getResponseBodyAsString());
			}
			completed = true;
			return post;
		} finally {
			if (!completed) {
				post.abort();
			}
		}
	}

	protected void execute(Runnable command) {
		executor.execute(command);
	}

	protected abstract String getAccept();

	private String getQueryString() {
		if (bindings.size() == 0)
			return query;
		String qry = query;
		for (String name : bindings.getBindingNames()) {
			String replacement = getReplacement(bindings.getValue(name));
			if (replacement != null) {
				String pattern = "[\\?\\$]" + name + "(?=\\W)";
				qry = qry.replaceAll(pattern, replacement);
			}
		}
		return qry;
	}

	private String getReplacement(Value value) {
		StringBuilder sb = new StringBuilder();
		if (value instanceof URI) {
			return appendValue(sb, (URI) value).toString();
		} else if (value instanceof Literal) {
			return appendValue(sb, (Literal) value).toString();
		} else {
			throw new IllegalArgumentException(
					"BNode references not supported by SPARQL end-points");
		}
	}

	private StringBuilder appendValue(StringBuilder sb, URI uri) {
		sb.append("<").append(uri.stringValue()).append(">");
		return sb;
	}

	private StringBuilder appendValue(StringBuilder sb, Literal lit) {
		sb.append('"');
		sb.append(lit.getLabel().replace("\"", "\\\""));
		sb.append('"');

		if (lit.getLanguage() != null) {
			sb.append('@');
			sb.append(lit.getLanguage());
		}

		if (lit.getDatatype() != null) {
			sb.append("^^<");
			sb.append(lit.getDatatype().stringValue());
			sb.append('>');
		}
		return sb;
	}

}