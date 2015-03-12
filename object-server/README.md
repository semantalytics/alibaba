Object Server
=============
 
 The HTTP object server is a resource oriented platform for hosting resources and RESTful services. It differs from other resource oriented frameworks because it allows resource paths to be dynamic and change over time. In AliBaba, request handler paths and their metadata are stored in a dynamic RDF store (and an accompanying blob store). This dynamic indirection between request-uri and code execution gives increased manageability and persistent control over the published URLs.

 When a request is received, the scheme and hierarchical part is used to find a resource by IRI in an RDF store, or an IRI prefix, ending in a slash. The matched resource's rdf:type is then used to determinate resource class and associated Java classes. The request method, absolute request URL, and Content-Type/Accept headers are used to determine the exact Java method to invoke. The result is then serialized as the HTTP response.
 
 To start the HTTP object server run the main class org.openrdf.http.object.Server.
 The server has a required command line options to assign the RDF data
 directory. Use command line options to assign ports and a server name. For details run the server with the '-h' option.

RDF Graph Store
---------------

    // GraphStore.java
    import org.openrdf.annotations.*;
    import org.openrdf.model.*;
    import org.openrdf.query.*;
    import org.openrdf.repository.object.*;
    import org.openrdf.OpenRDFException;
    import org.apache.http.*;
    import org.apache.http.message.BasicHttpResponse;
    @Iri("http://example.com/test/GraphStore")
    public abstract class GraphStore implements RDFObject {
        @Method("HEAD")
        @Path(".*")
        public HttpResponse head(@Param("0") String path) {
            HttpResponse head = new BasicHttpResponse(HttpVersion.HTTP_1_1, 200, "OK");
            head.addHeader("Cache-Control", "no-store");
            return head;
        }
        @Method("GET")
        @Path(".*")
        @Type({"text/turtle", "application/rdf+xml"})
        public GraphQueryResult get(@Param("0") String path) throws OpenRDFException {
            String base = this.getResource().stringValue();
            ObjectConnection con = this.getObjectConnection();
            ValueFactory vf = con.getValueFactory();
            URI graph = vf.createURI(base + path);
            GraphQuery qry = con.prepareGraphQuery(
                QueryLanguage.SPARQL,
                "CONSTRUCT { ?s ?p ?o } WHERE { GRAPH ?g { ?s ?p ?o } }"
            );
            qry.setBinding("g", graph);
            return qry.evaluate();
        }
        @Method("PUT")
        @Path(".*")
        public void put(
                @Param("0") String path,
                @Type({"text/turtle", "application/rdf+xml"}) GraphQueryResult dataset
        ) throws OpenRDFException {
            String base = this.getResource().stringValue();
            ObjectConnection con = this.getObjectConnection();
            ValueFactory vf = con.getValueFactory();
            URI graph = vf.createURI(base + path);
            con.clear(graph);
            con.add(dataset, graph);
        }
        @Method("POST")
        @Path(".*")
        public void post(
                @Param("0") String path,
                @Type({"text/turtle", "application/rdf+xml"}) GraphQueryResult dataset
        ) throws OpenRDFException {
            String base = this.getResource().stringValue();
            ObjectConnection con = this.getObjectConnection();
            ValueFactory vf = con.getValueFactory();
            URI graph = vf.createURI(base + path);
            con.add(dataset, graph);
        }
    }

    # META-INF/org.openrdf.concepts (empty)

    $ /opt/openrdf-sesame-2.8.0-beta2/bin/console.sh -d data
    Connected to data
    Commands end with '.' at the end of a line
    Type 'help.' for help
    > create nativerdf.
    No template called nativerdf found in /home/james/.aduna/openrdf-sesame-console/templates
    > create native.
    Please specify values for the following variables:
    Repository ID [native]: 
    Repository title [Native store]: 
    Triple indexes [spoc,posc]: 
    Repository created
    > open native.
    Opened repository 'native'
    native> sparql INSERT DATA { <http://localhost:8080/> a <http://example.com/test/GraphStore> }.
    Executing update...
    Update executed in 63 ms
    native> quit.
    Closing repository 'native'...
    Disconnecting from data
    Bye

    java -cp 'lib/*' org.openrdf.http.object.ServerControl -d data -i nativerdf -x http://localhost:8080/
    INFO: Serving http://localhost:8080/ from data/repositories/native/

    java -cp 'lib/*' org.openrdf.http.object.Server -d data -p 8080
    INFO: Scanning for concepts
    INFO: Serving http://localhost:8080/ from data/repositories/native/
    INFO: Restricted file system access in effect
    INFO: AliBaba ObjectServer/2.1 is binding to port 8080 
    INFO: AliBaba ObjectServer/2.1 started after 0.891 seconds


    curl -X PUT -H content-type:text/turtle --data-binary '<http://example.org/person/Mark_Twain> <http://example.org/relation/author> <http://example.org/books/Huckleberry_Finn> .' -i http://localhost:8080/finn
    curl --compress -H accept:text/turtle -v http://localhost:8080/finn
    curl --compress -H accept:application/rdf+xml -v http://localhost:8080/finn
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

 Annotated methods can return any registered concept, a concept Set, Model, GraphQueryResult,
 TupleQueryResult, InputStream, Readable, ReadableByteChannel, XMLEventReader,
 Document, Element, DocumentFragment, ByteArrayOutputStream, byte[], or String.
 They can include a body parameter of any of the previously listed types with a @type annotation and other parameters with the @query annotation with a query parameter name. Query parameters can be any datatype or concept type. A @query("*") can be used with a Map\<String, String[]\> for a complete set of query parameters. The method FileObject#toUri() returns the URI of the resource (without any query parameters). The annotation @type restricts the possible media types a method will produce or consume.
 
