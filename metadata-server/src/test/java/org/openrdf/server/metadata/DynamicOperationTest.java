package org.openrdf.server.metadata;

import org.openrdf.model.Model;
import org.openrdf.model.URI;
import org.openrdf.model.impl.LinkedHashModel;
import org.openrdf.model.vocabulary.RDF;
import org.openrdf.model.vocabulary.RDFS;
import org.openrdf.server.metadata.base.MetadataServerTestCase;

import com.sun.jersey.api.client.WebResource;

public class DynamicOperationTest extends MetadataServerTestCase {

	public void setUp() throws Exception {
		config.setCompileRepository(true);
		super.setUp();
	}

	public void testDynamicOperation() throws Exception {
		URI ICON = vf.createURI("urn:test:icon");
		String META = "http://www.openrdf.org/rdf/2009/metadata#";
		URI OPERATION = vf.createURI(META + "operation");
		Model rdf = new LinkedHashModel();
		rdf.add(ICON, RDF.TYPE, RDF.PROPERTY);
		rdf.add(ICON, RDFS.DOMAIN, RDFS.RESOURCE);
		rdf.add(ICON, OPERATION, vf.createLiteral("icon"));
		WebResource resource = client.path("/resource");
		WebResource icon = client.path("/icon");
		String uri = resource.getURI().toASCIIString();
		String icon_uri = icon.getURI().toASCIIString();
		rdf.add(vf.createURI(uri), ICON, vf.createURI(icon_uri));
		client.path("/schema.rdf").type("application/rdf+xml").put(rdf);
		icon.type("text/plain").put("my icon");
		WebResource resource_icon = resource.queryParam("icon", "");
		assertEquals("my icon", resource_icon.get(String.class));
	}
}