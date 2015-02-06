Object Server
=============
 
 The HTTP object server is a resource oriented platform for hosting resources and RESTful services. It differs from other resource oriented frameworks because it allows resource paths to be dynamic and change over time. In AliBaba, request handler paths and their metadata are stored in a dynamic RDF store (and an accompanying blob store). This dynamic indirection between request-uri and code execution gives increased manageability and persistent control over the published URLs.

 The HTTP Object Server includes two types of persistence stores:
 blobs are stored using the local file system and accessed through the local interface
 {{{http://java.sun.com/javase/6/docs/api/javax/tools/FileObject.html}FileObject}}
 and metadata is stored in an RDF store.

 When a request is received, the scheme and hierarchical part is used to find a resource by IRI in an RDF store, or a prefix, ending in a slash. The matched resource's rdf:type is then used to determinate relevant Java classes. The request method, absolute request URL, and Content-Type/Accept headers are used to determine the exact Java method to invoke. The result is then serialized as the HTTP response.
 
 To start the HTTP object server run the provided bin/object-server.sh (or
 .bat) file with the main class org.openrdf.http.object.Server.
 The server has optional command line options to assign the RDF data
 directory. Use command line options to enable ther server
 to read the schema from an RDF store. For details run the server with the '-h'
 option.
 
 <<Figure 2. CRUD RDF Implemented in Java>>

+---
// PUTGraphSupport.java
import java.io.*;
import org.openrdf.rio.*;
import org.openrdf.repository.object.*;
import org.openrdf.http.object.annotations.*;

public abstract class PUTGraphSupport implements RDFObject {
	@Method("PUT")
	public void putRDF(@type("application/rdf+xml") InputStream in) throws Exception {
		ObjectConnection con = this.getObjectConnection();
		con.clear(this.getResource());
		con.add(in, this.getResource().stringValue(), RDFFormat.RDFXML, this.getResource());
		con.addDesignation(this, NamedGraph.class);
	}

	@Method("PUT")
	public void putTurtle(@type("text/turtle") Reader in) throws Exception {
		ObjectConnection con = this.getObjectConnection();
		con.clear(this.getResource());
		con.add(in, this.getResource().stringValue(), RDFFormat.TURTLE, this.getResource());
		con.addDesignation(this, NamedGraph.class);
	}
}

// NamedGraph.java
import org.openrdf.model.*;
import org.openrdf.query.*;
import org.openrdf.query.impl.*;
import org.openrdf.repository.object.*;
import org.openrdf.annotations.*;
import org.openrdf.http.object.*;
import org.openrdf.http.object.annotations.*;

@Iri("http://data.leighnet.ca/rdf/2009/example#NamedGraph")
public abstract class NamedGraph implements RDFObject {
	@Method("GET")
	@Type({"text/turtle", "application/rdf+xml"})
	public GraphQueryResult getRDF() throws Exception {
		String qry = "CONSTRUCT {?s ?p ?o} WHERE {?s ?p ?o}";
		DatasetImpl ds = new DatasetImpl();
		ds.addDefaultGraph((URI) this.getResource());
		ObjectConnection con = this.getObjectConnection();
		GraphQuery query = con.prepareGraphQuery(qry);
		query.setDataset(ds);
		return query.evaluate();
	}

	@Method("DELETE")
	public void deleteRDF() throws Exception {
		ObjectConnection con = this.getObjectConnection();
		con.clear((URI) this.getResource());
		con.removeDesignation(this, NamedGraph.class);
	}
}

# META-INF/org.openrdf.behaviours
PUTGraphSupport = http://www.w3.org/2000/01/rdf-schema#Resource

# META-INF/org.openrdf.concepts (empty)
+---

 When the four files in Figure 2 are compiled into a jar and included on the command line
 when the server starts (or in the class path), RDF files uploaded in a PUT request will be added to the
 RDF store and these graphs will be made available as an alternate GET requests.
 In this case when an RDF file is PUT onto the server, the contents of the file are
 indexed in the RDF store using the target URL as the named graph. If a
 client asks for the contents of the graph in a different RDF format, the
 server will redirect the client and return the indexed graph in the
 requested format, as shown here.
 
---
 curl -X PUT -H Content-Type:text/turtle --data-binary @my-graph.ttl \
  http://localhost:8080/my-graph

 curl -L -H Accept:application/rdf+xml http://localhost:8080/my-graph
---

HTTP Headers

 HTTP request headers can be read by placing using the @Header annotation on a message parameter. The parameter will be populated with the header value when the request is received and method is called.

 Annotated methods can return any registered concept, a concept Set, Model, GraphQueryResult,
 TupleQueryResult, InputStream, Readable, ReadableByteChannel, XMLEventReader,
 Document, Element, DocumentFragment, ByteArrayOutputStream, byte[], or String.
 They can include a body parameter of any of the previously listed types with a @type annotation and other parameters with the @query annotation with a query parameter name. Query parameters can be any datatype or concept type. A @query("*") can be used with a Map\<String, String[]\> for a complete set of query parameters. The method FileObject#toUri() returns the URI of the resource (without any query parameters). The annotation @type restricts the possible media types a method will produce or consume.
 
