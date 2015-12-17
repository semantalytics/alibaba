AliBaba
=======

 AliBaba is an RDF application library for developing complex RDF storage applications. AliBaba is the next generation of the Elmo codebase. It is a collection of modules that provide simplified RDF store abstractions to accelerate development and facilitate application maintenance.
 
 Report any issues in the [OpenRDF JIRA issue tracker](https://openrdf.atlassian.net/).

 The program models used in today's software are growing in complexity. Most object-oriented models were not designed for the amount of growth and increased scope of today interconnected software agents. This is increasing the cost of new feature development and the cost of maintaining the software model. The object oriented paradigm was designed to model complex systems with complex behaviours, but many of its most powerful concepts (such as specialisation) are too often overlooked when designing distributed systems. By combining the flexibility and adaptivity of RDF with an powerful Object Oriented programming model, [AliBaba's object repository](https://bitbucket.org/openrdf/alibaba/src/master/object-repository/) is able to provide programmers with increased expressivity and a simplified subject-oriented programming environment. This can accelerate the time to market and reduce maintenance cost down the road.

[AliBaba's object server](https://bitbucket.org/openrdf/alibaba/src/master/object-server/) makes these objects available as resources on the Web. Using simple annotations object methods can be mapped to request handlers. This gives increased flexibility and URL manageability by allowing request handlers to be moved and shared among Web resources and endpoints.

 RDF is designed for metadata and is often accompanied by binary or text documents. Integrating binary and RDF store with consistent states is easier with [AliBaba's two phase commit BLOB store](https://bitbucket.org/openrdf/alibaba/src/master/blob-store).

 AliBaba includes many features not found in Sesame core to facility building complex, modern RDF applications.

Installation
------------

 There are two ways to acquire and run AliBaba: checkout the code repository or download the ZIP archive.

 If you want to check out the source code, you can do so on Bitbucket via the command git clone https://bitbucket.org/openrdf/alibaba.git. Once you have the source code checked out, you can then execute 'ant dist' from a command line in this top directory to create the ZIP file in the dist directory. Extract that ZIP file into a new directory.

 Alternatively, if you download a ZIP archive of a release from http://rdf4j.org/, extract it into a new directory.

 The ZIP file includes all the dependent jars in the lib/ directory and the AliBaba jar in the dist/ directory. The script bin/owl-compiler.bat/.sh can be used to compile an RDF model into Java interfaces (run with the -h flag to see the options). The script bin/object-server.bat/.sh can be used to start an AliBaba ObjectServer and the script bin/control-object-server.bat/.sh can be used to stop/control the server (run with the -h flag to see options).