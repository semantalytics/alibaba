/*
   Copyright (c) 2012 3 Round Stones Inc, Some Rights Reserved

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.

 */
package org.openrdf.http.object.fluid;

import java.util.ArrayList;
import java.util.List;

import javax.xml.transform.TransformerConfigurationException;

import org.openrdf.http.object.fluid.consumers.BooleanMessageWriter;
import org.openrdf.http.object.fluid.consumers.BufferedImageWriter;
import org.openrdf.http.object.fluid.consumers.ByteArrayMessageWriter;
import org.openrdf.http.object.fluid.consumers.ByteArrayStreamMessageWriter;
import org.openrdf.http.object.fluid.consumers.DOMMessageWriter;
import org.openrdf.http.object.fluid.consumers.DatatypeWriter;
import org.openrdf.http.object.fluid.consumers.DocumentFragmentMessageWriter;
import org.openrdf.http.object.fluid.consumers.FormMapMessageWriter;
import org.openrdf.http.object.fluid.consumers.FormStringMessageWriter;
import org.openrdf.http.object.fluid.consumers.GraphMessageWriter;
import org.openrdf.http.object.fluid.consumers.HttpEntityWriter;
import org.openrdf.http.object.fluid.consumers.HttpMessageWriter;
import org.openrdf.http.object.fluid.consumers.InputStreamBodyWriter;
import org.openrdf.http.object.fluid.consumers.ModelMessageWriter;
import org.openrdf.http.object.fluid.consumers.PrimitiveBodyWriter;
import org.openrdf.http.object.fluid.consumers.RDFObjectURIWriter;
import org.openrdf.http.object.fluid.consumers.ReadableBodyWriter;
import org.openrdf.http.object.fluid.consumers.ReadableByteChannelBodyWriter;
import org.openrdf.http.object.fluid.consumers.StringBodyWriter;
import org.openrdf.http.object.fluid.consumers.TupleMessageWriter;
import org.openrdf.http.object.fluid.consumers.URIListWriter;
import org.openrdf.http.object.fluid.consumers.VoidWriter;
import org.openrdf.http.object.fluid.consumers.XMLEventMessageWriter;
import org.openrdf.http.object.fluid.producers.BooleanMessageReader;
import org.openrdf.http.object.fluid.producers.BufferedImageReader;
import org.openrdf.http.object.fluid.producers.ByteArrayMessageReader;
import org.openrdf.http.object.fluid.producers.ByteArrayStreamMessageReader;
import org.openrdf.http.object.fluid.producers.DOMMessageReader;
import org.openrdf.http.object.fluid.producers.DatatypeReader;
import org.openrdf.http.object.fluid.producers.DocumentFragmentMessageReader;
import org.openrdf.http.object.fluid.producers.FormMapMessageReader;
import org.openrdf.http.object.fluid.producers.FormStringMessageReader;
import org.openrdf.http.object.fluid.producers.GraphMessageReader;
import org.openrdf.http.object.fluid.producers.HttpEntityReader;
import org.openrdf.http.object.fluid.producers.HttpMessageReader;
import org.openrdf.http.object.fluid.producers.InputStreamBodyReader;
import org.openrdf.http.object.fluid.producers.ModelMessageReader;
import org.openrdf.http.object.fluid.producers.PrimitiveBodyReader;
import org.openrdf.http.object.fluid.producers.RDFObjectURIReader;
import org.openrdf.http.object.fluid.producers.ReadableBodyReader;
import org.openrdf.http.object.fluid.producers.ReadableByteChannelBodyReader;
import org.openrdf.http.object.fluid.producers.StringBodyReader;
import org.openrdf.http.object.fluid.producers.TupleMessageReader;
import org.openrdf.http.object.fluid.producers.VoidReader;
import org.openrdf.http.object.fluid.producers.XMLEventMessageReader;
import org.openrdf.http.object.fluid.producers.base.URIListReader;
import org.openrdf.repository.object.ObjectConnection;
import org.openrdf.repository.object.ObjectService;
import org.openrdf.repository.object.ObjectServiceImpl;
import org.openrdf.repository.object.exceptions.ObjectStoreConfigException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Creates {@link FluidBuilder} to convert between media types.
 * 
 * @author James Leigh
 * 
 */
public class FluidFactory {
	private static final FluidFactory instance = new FluidFactory();
	static {
		instance.init();
	}

	public static FluidFactory getInstance() {
		return instance;
	}

	private final Logger logger = LoggerFactory.getLogger(FluidFactory.class);
	private List<Consumer<?>> consumers = new ArrayList<Consumer<?>>();
	private List<Producer> producers = new ArrayList<Producer>();
	private ObjectService service;

	private void init() {
		consumers.add(new RDFObjectURIWriter());
		consumers.add(new BooleanMessageWriter());
		consumers.add(new ModelMessageWriter());
		consumers.add(new GraphMessageWriter());
		consumers.add(new TupleMessageWriter());
		consumers.add(new DatatypeWriter());
		consumers.add(new StringBodyWriter());
		consumers.add(new VoidWriter());
		consumers.add(new PrimitiveBodyWriter());
		consumers.add(new HttpMessageWriter());
		consumers.add(new InputStreamBodyWriter());
		consumers.add(new ReadableBodyWriter());
		consumers.add(new ReadableByteChannelBodyWriter());
		consumers.add(new XMLEventMessageWriter());
		consumers.add(new ByteArrayMessageWriter());
		consumers.add(new ByteArrayStreamMessageWriter());
		consumers.add(new FormMapMessageWriter());
		consumers.add(new FormStringMessageWriter());
		consumers.add(new HttpEntityWriter());
		consumers.add(new BufferedImageWriter());
		consumers.add(URIListWriter.RDF_URI);
		consumers.add(URIListWriter.NET_URL);
		consumers.add(URIListWriter.NET_URI);
		try {
			consumers.add(new DocumentFragmentMessageWriter());
			consumers.add(new DOMMessageWriter());
		} catch (TransformerConfigurationException e) {
			throw new AssertionError(e);
		}
		producers.add(URIListReader.RDF_URI);
		producers.add(URIListReader.NET_URL);
		producers.add(URIListReader.NET_URI);
		producers.add(new RDFObjectURIReader());
		producers.add(new ModelMessageReader());
		producers.add(new GraphMessageReader());
		producers.add(new TupleMessageReader());
		producers.add(new BooleanMessageReader());
		producers.add(new DatatypeReader());
		producers.add(new StringBodyReader());
		producers.add(new VoidReader());
		producers.add(new PrimitiveBodyReader());
		producers.add(new FormMapMessageReader());
		producers.add(new FormStringMessageReader());
		producers.add(new HttpMessageReader());
		producers.add(new InputStreamBodyReader());
		producers.add(new ReadableBodyReader());
		producers.add(new ReadableByteChannelBodyReader());
		producers.add(new XMLEventMessageReader());
		producers.add(new ByteArrayMessageReader());
		producers.add(new ByteArrayStreamMessageReader());
		producers.add(new DOMMessageReader());
		producers.add(new DocumentFragmentMessageReader());
		producers.add(new HttpEntityReader());
		producers.add(new BufferedImageReader());
	}

	public FluidBuilder builder(ObjectConnection con) {
		return new FluidBuilder(consumers, producers, con);
	}

	public FluidBuilder builder() {
		return new FluidBuilder(consumers, producers, getObjectService());
	}

	private synchronized ObjectService getObjectService() {
		if (service == null) {
			try {
				service = new ObjectServiceImpl();
			} catch (ObjectStoreConfigException e) {
				logger.warn(e.toString(), e);
			}
		}
		return service;
	}

}
