/*
 * Copyright (c) 2009, James Leigh All rights reserved.
 * Copyright (c) 2011 Talis Inc., Some rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * - Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 * - Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution. 
 * - Neither the name of the openrdf.org nor the names of its contributors may
 *   be used to endorse or promote products derived from this software without
 *   specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * 
 */
package org.openrdf.server.object.fluid.producers;

import java.io.FilterInputStream;
import java.io.IOException;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.Charset;

import org.openrdf.query.QueryResultHandlerException;
import org.openrdf.query.TupleQueryResult;
import org.openrdf.query.impl.TupleQueryResultImpl;
import org.openrdf.query.resultio.QueryResultParseException;
import org.openrdf.query.resultio.TupleQueryResultFormat;
import org.openrdf.query.resultio.TupleQueryResultParser;
import org.openrdf.query.resultio.TupleQueryResultParserFactory;
import org.openrdf.query.resultio.TupleQueryResultParserRegistry;
import org.openrdf.query.resultio.helpers.QueryResultCollector;
import org.openrdf.server.object.fluid.producers.base.MessageReaderBase;

/**
 * Reads tuple results.
 * 
 * @author James Leigh
 * 
 */
public class TupleMessageReader
		extends
		MessageReaderBase<TupleQueryResultFormat, TupleQueryResultParserFactory, TupleQueryResult> {

	public TupleMessageReader() {
		super(TupleQueryResultParserRegistry.getInstance(),
				TupleQueryResult.class);
	}

	@Override
	public TupleQueryResult readFrom(TupleQueryResultParserFactory factory,
			final ReadableByteChannel ch, Charset charset, String base)
			throws QueryResultParseException, QueryResultHandlerException,
			IOException {
		if (ch == null)
			return null;
		QueryResultCollector col = new QueryResultCollector();
		TupleQueryResultParser parser = factory.getParser();
		parser.setQueryResultHandler(col);
		parser.parseQueryResult(new FilterInputStream(Channels.newInputStream(ch)) {
			@Override
			public int available() throws IOException {
				int available = super.available();
				// https://sourceforge.net/p/opencsv/bugs/108/
				if (available == 0 && ch.isOpen())
					return 1; // stream is closed if nothing is available
				return available;
			}

			@Override
			public String toString() {
				return in.toString();
			}
		});
		col.endQueryResult();
		if (col.getBindingNames().isEmpty())
			return null;
		return new TupleQueryResultImpl(col.getBindingNames(), col.getBindingSets());
	}

}
