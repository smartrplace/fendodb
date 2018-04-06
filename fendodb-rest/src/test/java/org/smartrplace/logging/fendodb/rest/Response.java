/**
 * Copyright 2018 Smartrplace UG
 *
 * FendoDB is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * FendoDB is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.smartrplace.logging.fendodb.rest;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;

import javax.servlet.ServletOutputStream;

class Response {
	
	final ProxyOutputStream streamOut = new ProxyOutputStream();
	final ProxyWriter writerOut = new ProxyWriter();
	
	String getResponseAsString() {
		final String writerResult = writerOut.getResult();
		if (writerResult != null && !writerResult.isEmpty())
			return writerResult;
		return streamOut.getResultAsString();
	}
	
	static class ProxyOutputStream extends ServletOutputStream {

		private final ByteArrayOutputStream out = new ByteArrayOutputStream();

		@Override
		public void write(int b) throws IOException {
			out.write(b);
		}
		
		public byte[] getResult() {
			return out.toByteArray();
		}
		
		public String getResultAsString() {
			return out.size() == 0 ? null : new String(out.toByteArray(), StandardCharsets.UTF_8);
		}
		
	}
	
	static class ProxyWriter extends PrintWriter {

		ProxyWriter() {
			super(new StringWriter());
		}

		public String getResult() {
			return ((StringWriter) out).toString();
		}
		
	}

}
