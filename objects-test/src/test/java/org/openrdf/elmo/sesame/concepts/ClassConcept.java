package org.openrdf.elmo.sesame.concepts;

import java.util.Set;

import org.openrdf.elmo.annotations.rdf;

/** The class of classes. */
@rdf("http://www.w3.org/2000/01/rdf-schema#Class")
public interface ClassConcept {


	/** The subject is a subclass of a class. */
	@rdf("http://www.w3.org/2000/01/rdf-schema#subClassOf")
	public abstract Set<ClassConcept> getRdfsSubClassOf();

	/** The subject is a subclass of a class. */
	public abstract void setRdfsSubClassOf(Set<ClassConcept> value);

}
