/**
 * ﻿Copyright 2018 Smartrplace UG
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
