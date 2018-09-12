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
package org.smartrplace.logging.fendodb.tagging.impl;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.ogema.core.model.Resource;
import org.ogema.core.resourcemanager.ResourceAccess;
import org.smartrplace.logging.fendodb.CloseableDataRecorder;
import org.smartrplace.logging.fendodb.FendoTimeSeries;
import org.smartrplace.logging.fendodb.tagging.api.TaggingUtils;

class ContinuousTagger implements Consumer<FendoTimeSeries>, AutoCloseable {

	private final CloseableDataRecorder recorder;
	private final ResourceAccess ra;
	final Path path;
	
	ContinuousTagger(CloseableDataRecorder recorder, ResourceAccess ra) throws Exception {
		this.recorder = recorder;
		this.ra = ra;
		this.path = recorder.getPath();
		// register itself with recorder; not part of the public API, although it could be made into
		final Method m = recorder.getClass().getMethod("registerListener", Consumer.class);
		m.setAccessible(true);
		m.invoke(recorder, this);
	}
	
	@Override
	public void close() {
		try {
			final Method m = recorder.getClass().getMethod("removeListener", Consumer.class);
			m.setAccessible(true);
			m.invoke(recorder, this);
		} catch (Exception ignore) {}
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Override
	public void accept(FendoTimeSeries t) {
		try {
			final Resource r = ra.getResource(t.getPath());
			if (r == null)
				return;
			final Map<String, List<String>> tags = TaggingUtils.getResourceTags(r, ra);
			t.setProperties((Map) tags);
		} catch (SecurityException ignore) {}
	}	
	
}
