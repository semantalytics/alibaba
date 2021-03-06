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

import java.io.IOException;
import java.nio.channels.ReadableByteChannel;

/**
 * When a {@link Fluid} does not support a desired Java or media type, it is
 * converted into a {@link ReadableByteChannel} and parsed to the desired Java
 * type.
 * 
 * @author James Leigh
 * 
 */
class ChannelFluid extends AbstractFluid {
	private final Fluid fluid;
	private final FluidBuilder builder;

	public ChannelFluid(Fluid fluid, FluidBuilder builder) {
		assert fluid != null;
		assert builder != null;
		this.fluid = fluid;
		this.builder = builder;
	}

	public FluidType getFluidType() {
		return fluid.getFluidType();
	}

	public String getSystemId() {
		return fluid.getSystemId();
	}

	public String toString() {
		return fluid.toString();
	}

	public void asVoid() throws IOException, FluidException {
		fluid.asVoid();
	}

	@Override
	public String toMedia(FluidType ftype) {
		String ret = fluid.toMedia(ftype);
		if (ret != null)
			return ret;
		String[] cmt = getChannelMedia(ftype.media());
		return builder.media(cmt).toMedia(ftype);
	}

	@Override
	public Object as(FluidType ftype) throws IOException, FluidException {
		try {
			if (fluid.toMedia(ftype) != null)
				return fluid.as(ftype);
			String[] cmt = getChannelMedia(ftype.media());
			ReadableByteChannel in = fluid.asChannel(cmt);
			return builder.channel(in, fluid.getSystemId(), cmt).as(ftype);
		} catch (RuntimeException e) {
			throw handle(e);
		} catch (Error e) {
			throw handle(e);
		} catch (IOException e) {
			throw handle(e);
		} catch (FluidException e) {
			throw handle(e);
		}
	}

	private String[] getChannelMedia(String[] media) {
		String channelMedia = fluid.toChannelMedia(media);
		if (channelMedia != null)
			return new String[] { channelMedia };
		return getFluidType().media();
	}

	protected <E extends Throwable> E handle(E cause) throws IOException,
			FluidException {
		try {
			asVoid();
			return cause;
		} catch (RuntimeException e) {
			e.initCause(cause);
			throw e;
		} catch (Error e) {
			e.initCause(cause);
			throw e;
		} catch (FluidException e) {
			e.initCause(cause);
			throw e;
		} catch (IOException e) {
			e.initCause(cause);
			throw e;
		}
	}

}
