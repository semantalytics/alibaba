package org.openrdf.repository.object.vocabulary;

import org.openrdf.model.URI;
import org.openrdf.model.impl.URIImpl;

public class OBJ {
	public static final String NAMESPACE = "http://www.openrdf.org/rdf/2009/03/object#";
	public static final URI MESSAGE = new URIImpl(NAMESPACE + "Message");
	public static final URI METHOD = new URIImpl(NAMESPACE + "Method");
	public static final URI OBJECT_RESPONSE = new URIImpl(NAMESPACE
			+ "objectResponse");
	public static final URI LITERAL_RESPONSE = new URIImpl(NAMESPACE
			+ "literalResponse");
	public static final URI TARGET = new URIImpl(NAMESPACE + "target");
	public static final URI IMPORTS = new URIImpl(NAMESPACE + "imports");
	public static final URI GROOVY = new URIImpl("http://www.openrdf.org/rdf/2009/03/object#groovy");
	public static final URI JAVA = new URIImpl("http://www.openrdf.org/rdf/2009/03/object#java");
	public static final URI LOCALIZED = new URIImpl(NAMESPACE + "localized");
	public static final URI NAME = new URIImpl(NAMESPACE + "name");
	public static final URI LITERAL_TRIGGER = new URIImpl(NAMESPACE + "literalTrigger");
	public static final URI OBJECT_TRIGGER = new URIImpl(NAMESPACE + "objectTrigger");
	public static final URI COMPONENT_TYPE = new URIImpl(NAMESPACE + "componentType");

	private OBJ() {
		// prevent instantiation
	}

}
