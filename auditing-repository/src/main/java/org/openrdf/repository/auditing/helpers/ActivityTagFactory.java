package org.openrdf.repository.auditing.helpers;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Calendar;
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
 * Generates unique URI for every activity. This class assigns the
 * prov:endedAtTime when the activity ends.
 * 
 * @author James Leigh
 * 
 */
public class ActivityTagFactory implements ActivityFactory {
	public static final URI ENDED_AT = new URIImpl(
			"http://www.w3.org/ns/prov#endedAtTime");
	private static final String uid = "t"
			+ Long.toHexString(System.currentTimeMillis()) + "x";
	private static final AtomicLong seq = new AtomicLong(0);
	private final DatatypeFactory df;
	private final String space;
	private String namespace;
	private GregorianCalendar date;

	public ActivityTagFactory() {
		String userinfo = System.getProperty("user.name");
		if (userinfo != null) {
			userinfo = userinfo.replaceAll("^[^a-zA-Z0-9.-]+", "");
			userinfo = userinfo.replaceAll("[^a-zA-Z0-9.-]+$", "");
			userinfo = userinfo.replaceAll("[^a-zA-Z0-9.-]", "_");
		}
		if (userinfo == null || userinfo.length() == 0) {
			userinfo = "";
		} else {
			userinfo = userinfo + "@";
		}
		String authority = "localhost";
		try {
			InetAddress localMachine = InetAddress.getLocalHost();
			authority = localMachine.getHostName();
			authority = authority.replaceAll("^[^a-zA-Z0-9.-]+", "");
			authority = authority.replaceAll("[^a-zA-Z0-9.-]+$", "");
			authority = authority.replaceAll("[^a-zA-Z0-9.-]", "_");
			authority = authority.toLowerCase();
		} catch (UnknownHostException e) {
			// ignore
		}
		space = "tag:" + userinfo + authority + ",";
		df = createDatatypeFactory();
	}

	public URI createActivityURI(URI bundle, ValueFactory vf) {
		if (bundle != null)
			return vf.createURI(bundle.stringValue() + "#provenance");
		String local = uid + seq.getAndIncrement();
		return vf.createURI(getNamespace() + local + "#provenance");
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

	private synchronized String getNamespace() {
		GregorianCalendar cal = new GregorianCalendar();
		if (date == null || date.get(Calendar.DATE) != cal.get(Calendar.DATE)
				|| date.get(Calendar.MONTH) != cal.get(Calendar.MONTH)
				|| date.get(Calendar.YEAR) != cal.get(Calendar.YEAR)) {
			date = cal;
			return namespace = space + date.get(Calendar.YEAR) + "-"
					+ zero(date.get(Calendar.MONTH) + 1) + "-"
					+ zero(date.get(Calendar.DATE)) + ":";
		}
		return namespace;
	}

	private String zero(int number) {
		if (number < 10)
			return "0" + number;
		return String.valueOf(number);
	}

}
