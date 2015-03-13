Object Server
=============
 
 The HTTP object server is a resource oriented platform for hosting resources and RESTful services. It differs from other resource oriented frameworks because it allows resource paths to be dynamic and change over time. In AliBaba, request handler paths and their metadata are stored in a dynamic RDF store (and an accompanying blob store). This dynamic indirection between request-uri and code execution gives increased manageability and persistent control over the published URLs.

 When a request is received, the scheme and hierarchical part is used to find a resource by IRI in an RDF store, or an IRI prefix ending in a slash. The matched resource's rdf:type is then used to determinate resource class and associated Java classes. The request method, absolute request URL, and Content-Type/Accept headers are used to determine a Java method to invoke. The result is then serialized as the HTTP response.
 
 To start the HTTP object server run the main class org.openrdf.http.object.Server.
 The server has a required command line options to assign the RDF data
 directory. Use command line options to assign ports and a server name. For details run the server with the '-h' option.

RDF Graph Store
---------------

 Create request handlers in Java and assign a resource class using the @Iri annotation. The @Method annotation is used to identify the request method and is required. Use the @Path annotation to both filter, validate, and extract path and query parameters using a regular expression. The @Param annotation can then be used to select those parameters. @Param can be passed a regular expression group (number or name) or a query string parameter name. Use @Type annotation on methods to provide a response content-type and indicate the returned object is just a response body (not the entire response). Use @Type annotation on a method parameters to indicate supported request content-types and to be populated with the request body.

 @Type annotated methods can return, and @Type annotated method parameters can be, any registered concept, a concept Set, Model, GraphQueryResult, TupleQueryResult, HttpResponse, InputStream, Readable, ReadableByteChannel, XMLEventReader, Document, Element, DocumentFragment, ByteArrayOutputStream, byte[], or String.

 The @Method("HEAD") annotation can be placed on methods that will include response headers that should be included in other responses. Content response headers from the head response will only be included in HEAD and GET responses. These header can be used for setting Cache-Control and ETag response headers that will be used to control and validate the built-in server side cache.

    // GraphStore.java
    import org.openrdf.annotations.*;
    import org.openrdf.model.*;
    import org.openrdf.query.*;
    import org.openrdf.repository.object.*;
    import org.openrdf.OpenRDFException;
    import org.apache.http.*;
    import org.apache.http.message.BasicHttpResponse;
    @Iri("http://example.org/schema/GraphStore")
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
        @Method("DELETE")
        @Path(".*")
        public void delete(
                @Param("0") String path,
                @Type({"text/turtle", "application/rdf+xml"}) GraphQueryResult dataset
        ) throws OpenRDFException {
            String base = this.getResource().stringValue();
            ObjectConnection con = this.getObjectConnection();
            ValueFactory vf = con.getValueFactory();
            URI graph = vf.createURI(base + path);
            con.clear(graph);
        }
    }

    # META-INF/org.openrdf.concepts (empty)

 Compile the above class and create an empty META-INF/org.openrdf.concepts. These files will be need to be included in the classpath when the server starts.

    $ javac -cp 'lib/*:dist/*' GraphStore.java

 Create an RDF store, by using the Sesame Console (provided in the Sesame SDK), to store the resources. Then insert a resource that will be served as the previously defined GraphStore.

    $ mkdir data
    $ /opt/openrdf-sesame-2.8.0-beta2/bin/console.sh -d data
    Connected to data
    Commands end with '.' at the end of a line
    Type 'help.' for help
    > create native.
    Please specify values for the following variables:
    Repository ID [native]: 
    Repository title [Native store]: 
    Triple indexes [spoc,posc]: 
    Repository created
    > open native.
    Opened repository 'native'
    native> sparql INSERT DATA { <http://localhost:8080/graphs/> a <http://example.org/schema/GraphStore> }.
    Executing update...
    Update executed in 63 ms
    native> quit.
    Closing repository 'native'...
    Disconnecting from data
    Bye

 Once the RDF store is created, assign the URL prefix to the store. Any HTTP requests that start with this prefix will be resolved using the RDF store provided.

    java -cp 'lib/*:dist/*' org.openrdf.http.object.ServerControl -d data -i native -x http://localhost:8080/
    INFO: Serving http://localhost:8080/ from data/repositories/native/

 Start the using and include the GraphStore.class in the classpath.

    java -cp '.:lib/*:dist/*' org.openrdf.http.object.Server -d data -p 8080
    INFO: Scanning for concepts
    INFO: Serving http://localhost:8080/ from data/repositories/native/
    INFO: Restricted file system access in effect
    INFO: AliBaba ObjectServer/2.1 is binding to port 8080 
    INFO: AliBaba ObjectServer/2.1 started after 0.891 seconds

 Once the server is runnig, using another terminal, create a new RDF graph by PUTting a text/turtle representation to a URL. Then GET the RDF graph back in the supported formats.

 Note that if lib/ or dist/ directories are within the tmpdir the server will fail to start with AccessControlExceptions.

    $ curl -X PUT -H content-type:text/turtle --data-binary '<http://example.org/person/Mark_Twain> <http://example.org/relation/author> <http://example.org/books/Huckleberry_Finn> .' -i http://localhost:8080/graphs/twain
    HTTP/1.1 204 No Content
    $ curl -H accept:text/turtle http://localhost:8080/graphs/twain
    $ curl -H accept:application/rdf+xml http://localhost:8080/graphs/twain

 If you get a 405 error check that you included the GraphStore.class in the classpath. You should see a "Scanning" message in the server output. If not you either don't have the classpath setup or you are missing the META-INF/org.openrdf.concepts file.
 
