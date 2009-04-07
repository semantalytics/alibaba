package org.openrdf.server.metadata.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import javax.ws.rs.HttpMethod;

import org.openrdf.repository.object.annotations.rdf;

@rdf("http://www.openrdf.org/rdf/2009/04/metadata#operation")
@Target( { ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
@HttpMethod(HttpMethod.POST)
public @interface operation {
	String[] value() default {};

}
