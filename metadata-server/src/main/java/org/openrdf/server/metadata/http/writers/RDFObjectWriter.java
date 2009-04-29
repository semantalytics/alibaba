package org.openrdf.server.metadata.http.writers;

import static org.openrdf.query.QueryLanguage.SPARQL;

import java.io.IOException;
import java.io.OutputStream;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.ext.Provider;

import org.openrdf.model.Resource;
import org.openrdf.query.GraphQuery;
import org.openrdf.query.GraphQueryResult;
import org.openrdf.query.MalformedQueryException;
import org.openrdf.query.QueryEvaluationException;
import org.openrdf.query.TupleQueryResultHandlerException;
import org.openrdf.repository.RepositoryException;
import org.openrdf.repository.object.ObjectConnection;
import org.openrdf.repository.object.RDFObject;
import org.openrdf.rio.RDFHandlerException;

@Provider
public class RDFObjectWriter implements MessageBodyWriter<RDFObject> {
	private static final String DESCRIBE_SELF = "CONSTRUCT {$self ?pred ?obj}\n"
			+ "WHERE {$self ?pred ?obj}";
	private GraphMessageWriter delegate;

	public RDFObjectWriter() {
		delegate = new GraphMessageWriter();
	}

	public long getSize(RDFObject t, MediaType mediaType) {
		return -1;
	}

	public boolean isWriteable(Class<?> type, MediaType mediaType) {
		Class<GraphQueryResult> t = GraphQueryResult.class;
		if (!delegate.isWriteable(t, mediaType))
			return false;
		return RDFObject.class.isAssignableFrom(type);
	}

	public String getContentType(Class<?> type, MediaType mediaType) {
		return delegate.getContentType(null, mediaType);
	}

	public void writeTo(RDFObject result, String base, MediaType mediaType,
			OutputStream out) throws IOException, RDFHandlerException,
			QueryEvaluationException, TupleQueryResultHandlerException,
			RepositoryException {
		ObjectConnection con = result.getObjectConnection();
		Resource resource = result.getResource();
		try {
			GraphQuery query = con.prepareGraphQuery(SPARQL, DESCRIBE_SELF);
			query.setBinding("self", resource);
			delegate.writeTo(query.evaluate(), base, mediaType, out);
		} catch (MalformedQueryException e) {
			throw new AssertionError(e);
		}
	}

}
