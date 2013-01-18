Auditing Repository
===================

 The auditing repository is a repository wrapper to track changes and store provenance data in the RDF store. The Auditing Repository uses a vocabulary based on the [The PROV Ontology](http://www.w3.org/TR/prov-o/). The audit: namespace used by the auditing repository has a namespace of "http://www.openrdf.org/rdf/2012/auditing#".

 Entity URIs (URIs with the fragment identifier removed) that are present in a DELETE/INSERT clause as a subject or graph (with or without a fragment identifier) are associated with an activity, through the [prov:wasGeneratedBy](http://www.w3.org/TR/prov-o/#wasGeneratedBy) relationship. The URI of the activity is assigned using an org.openrdf.repository.auditing.ActivityFactory. The method createActivityURI(URI, ValueFactory) is provided the default insert graph/context (or bundle), if any, and returns an activity URI for the current transaction. Add and remove operations with an explicit quad (subject, predicate, object, and context and not in an update operations) have no effect on the auditing repository, they are passed through as-is.

 The bundle can be assigned by setting the default insert context, in the AuditingRepositoryConnection, or assigned in a parent ContextAwareRepositoryConnection, or in ObjectConnection#setVersionBundle(), or assigned on a particular Operation's Dataset's defaultInsertGraph. If no bundle is assigned, it is derived from the activity URI created through the repository's activityFactory's createActivityURI method. If no bundle is assigned, the returned activity URI is used as the default insert graph (and therefore the bundle), with the fragement identifier removed. The default ActivityFactory also assigns a [prov:endedAtTime](http://www.w3.org/TR/prov-o/#endedAtTime) to the activity. Therefore, the pattern ?entity prov:wasGeneratedBy/prov:endedAtTime ?lastmodified would provide the last modified timestamp of any entity URI (modified in this way).

 When the transaction is over every entity URI is also represented with a [prov:specializationOf](http://www.w3.org/TR/prov-o/#specializationOf) resource, linked from the activity with [prov:generated](http://www.w3.org/TR/prov-o/#generated), and previous [prov:wasGeneratedBy](http://www.w3.org/TR/prov-o/#wasGeneratedBy) relationships are removed. This ensures the prov:wasGeneratedBy remains functional.

 An example of a bundle, with the skos:Concept <sun> inserted, is shown below.

    GRAPH <b1> {
        <b1> prov:wasGeneratedBy <b1#activity> .
    
        <b1#activity>
            prov:generated <b1#!sun> ;
            prov:endedAtTime "2012-11-08T15:07:24.583Z"^^xsd:dateTime .
    
        <b1#!sun> prov:specializationOf <sun> .
    
        <sun> a skos:Concept ;
            prov:wasGeneratedBy <b1#activity> ;
            skos:definition "The great luminary" ;
            skos:prefLabel "Sun" .
    }

 A custom ActivityFactory can be used to listen for activity started and ended events. The started event is fired within a transaction but before anything is written to store. The ended event is fired when an activity is been committed. The provided ActivityTagFactory and ActivitySequenceFactory append a fragment identifier to the bundle URI to create the activity URI. The ActivityTagFactory will generate a URI in the tag: scheme if there is no bundle. The ActivitySequenceFactory will append a semi unique local identifier to a given namespace if there is no bundle. Both will insert a [prov:endedAtTime](http://www.w3.org/TR/prov-o/#endedAtTime) when the transaction is committed.

 Furthermore, when combined with the auditing sail and a delete graph pattern, of a single entity, is removed from the RDF store those triples of the entity are reified, using the [audit:without](http://www.openrdf.org/rdf/2012/auditing#without) relationship from the specialized entity resource and [audit:with](http://www.openrdf.org/rdf/2012/auditing#with) from the specialized entity resource that inserted it (if the triple is being removed from a graph without a fragment identifier). The auditing sail inserts [prov:wasRevisionOf](http://www.w3.org/TR/prov-o/#wasRevisionOf) to link a specialized entity resource to its previous version (if the entity is not a bundle itself), when previous prov:wasGeneratedBy relationships are removed. The auditing sail also links the bundle to other graphs that had a triple removed with [prov:wasInfluencedBy](http://www.w3.org/TR/prov-o/#wasInfluencedBy).

 Below is an example of a bundle that modified the skos:Concept <sun>. It should also be noted that the bundle <b1> is modified as described in the DELETE/INSERT statement below.

    GRAPH <b2> {
    
        <b2> prov:wasGeneratedBy <b2#activity> ;
            prov:wasInfluencedBy <b1> .
    
        <b2#activity>
            prov:generated <b2#!sun> ;
            prov:endedAtTime "2012-11-08T15:19:31.295Z"^^xsd:dateTime .
    
        <b2#!sun> prov:specializationOf <sun> ;
            prov:wasRevisionOf <b1#!sun> ;
            audit:without <b2#5eef4c8f> .
        <b2#5eef4c8f>
            rdf:subject <sun> ;
            rdf:predicate skos:definition ;
            rdf:object "The great luminary" .
        <sun>
            prov:wasGeneratedBy <b2#activity> ;
            skos:definition "The lamp of day" .
    }

    PREFIX skos:<http://www.w3.org/2004/02/skos/core#>
    PREFIX prov:<http://www.w3.org/TR/prov-o/#>
    PREFIX audit:<http://www.openrdf.org/rdf/2012/auditing#>
    
    WITH <b1>
    DELETE {
        <sun> skos:definition "The great luminary" ;
            prov:wasGeneratedBy <b1#activity> .
    } INSERT {
        <b1#!sun> audit:with <b2#5eef4c8f>
    } WHERE {};

 The auditing repository can also maintain a list of the most recent bundles using the type of [audit:RecentBundle](http://www.openrdf.org/rdf/2012/auditing#RecentBundle) and can also purge bundles that do not contribute to the current state of any entity (all inserted triples, if any, have since been removed). To enable purging of obsolete bundles the purge after duration must be set and bundles must have a [prov:endedAtTime](http://www.w3.org/TR/prov-o/#endedAtTime) property of earlier then the current time minus the purge after duration.

 Users are encouraged to add additional metadata of the activity into the RDF store using [The PROV Ontology](http://www.w3.org/TR/prov-o/). To ensure the purging of obsolete activity graphs function as expected, the PROV vocab should be used (all predicates must be in the "http://www.w3.org/ns/prov#" namespace or rdf:type) and/or a fragment identifier with the bundle used as the subject.

 The auditing repository should be used in conjunction with the auditing sail to track both added and removed triples.

