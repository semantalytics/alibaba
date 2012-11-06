package org.openrdf.repository.auditing.helpers;

import java.util.GregorianCalendar;
import java.util.concurrent.atomic.AtomicLong;

import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;

import org.openrdf.model.URI;
import org.openrdf.model.ValueFactory;
import org.openrdf.model.impl.URIImpl;
import org.openrdf.model.vocabulary.RDF;
import org.openrdf.repository.RepositoryConnection;
import org.openrdf.repository.RepositoryException;
import org.openrdf.repository.auditing.ActivityFactory;

public class ActivitySequenceFactory implements ActivityFactory {
	private static final String ACTIVITY = "http://www.openrdf.org/rdf/2012/auditing#Activity";
	public static final URI ENDED_AT = new URIImpl("http://www.w3.org/ns/prov#endedAtTime");
	private final String uid = "t"
			+ Long.toHexString(System.currentTimeMillis()) + "x";
	private final AtomicLong seq = new AtomicLong(0);
	private final DatatypeFactory df;
	private final String ns;

	public ActivitySequenceFactory(String ns) {
		this.ns = ns;
		df = createDatatypeFactory();
	}

	public URI createActivityURI(ValueFactory vf) {
		return vf.createURI(ns, uid + seq.getAndIncrement() + "#provenance");
	}

	public void activityStarted(URI provenance, URI activityGraph, RepositoryConnection con)
			throws RepositoryException {
		ValueFactory vf = con.getValueFactory();
		con.add(activityGraph, RDF.TYPE, vf.createURI(ACTIVITY), activityGraph);
	}

	public void activityEnded(URI provenance, URI activityGraph,
			RepositoryConnection con) throws RepositoryException {
		if (df != null) {
			ValueFactory vf = con.getValueFactory();
			XMLGregorianCalendar now = df
					.newXMLGregorianCalendar(new GregorianCalendar());
			con.add(provenance, ENDED_AT, vf.createLiteral(now), activityGraph);
		}
	}

	private DatatypeFactory createDatatypeFactory() {
		try {
			return DatatypeFactory.newInstance();
		} catch (DatatypeConfigurationException e) {
			return null;
		}
	}

}
