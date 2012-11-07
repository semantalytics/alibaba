package org.openrdf.repository.auditing.helpers;

import java.util.GregorianCalendar;
import java.util.concurrent.atomic.AtomicLong;

import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;

import org.openrdf.model.URI;
import org.openrdf.model.ValueFactory;
import org.openrdf.model.impl.URIImpl;
import org.openrdf.repository.RepositoryConnection;
import org.openrdf.repository.RepositoryException;
import org.openrdf.repository.auditing.ActivityFactory;

/**
 * Generates a unique local identifier with the provided namespace for every
 * activity. This class assigns the prov:endedAtTime when the activity ends.
 * 
 * @author James Leigh
 * 
 */
public class ActivitySequenceFactory implements ActivityFactory {
	public static final URI ENDED_AT = new URIImpl(
			"http://www.w3.org/ns/prov#endedAtTime");
	private final String uid = "t"
			+ Long.toHexString(System.currentTimeMillis()) + "x";
	private final AtomicLong seq = new AtomicLong(0);
	private final DatatypeFactory df;
	private final String ns;

	public ActivitySequenceFactory(String ns) {
		this.ns = ns;
		df = createDatatypeFactory();
	}

	public URI createActivityURI(URI bundle, ValueFactory vf) {
		if (bundle == null)
			return vf.createURI(ns + uid + seq.getAndIncrement()
					+ "#provenance");
		return vf.createURI(bundle.stringValue() + "#provenance");
	}

	public void activityStarted(URI activity, URI bundle,
			RepositoryConnection con) throws RepositoryException {
		// do nothing
	}

	public void activityEnded(URI activity, URI bundle, RepositoryConnection con)
			throws RepositoryException {
		if (df != null) {
			ValueFactory vf = con.getValueFactory();
			XMLGregorianCalendar now = df
					.newXMLGregorianCalendar(new GregorianCalendar());
			con.add(activity, ENDED_AT, vf.createLiteral(now), bundle);
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
