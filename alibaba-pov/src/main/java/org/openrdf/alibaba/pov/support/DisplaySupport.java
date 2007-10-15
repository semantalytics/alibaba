package org.openrdf.alibaba.pov.support;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;

import org.openrdf.alibaba.exceptions.AlibabaException;
import org.openrdf.alibaba.exceptions.BadRequestException;
import org.openrdf.alibaba.pov.Display;
import org.openrdf.alibaba.pov.DisplayBehaviour;
import org.openrdf.alibaba.vocabulary.POV;
import org.openrdf.elmo.annotations.rdf;

@rdf(POV.NS + "Display")
public class DisplaySupport implements DisplayBehaviour {

	public DisplaySupport(Display display) {
	}

	public Collection<?> getValuesOf(Object resource) throws AlibabaException {
		return new ArrayList<Object>(Arrays.asList(resource));
	}

	public void setValuesOf(Object resource, Collection<?> values)
			throws AlibabaException {
		if (!getValuesOf(resource).equals(values)) {
			throw new BadRequestException("Cannot set values: " + values);
		}
	}
}
