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
package org.smartrplace.logging.fendodb.source;

import org.smartrplace.logging.fendodb.DataRecorderReference;

import de.iwes.widgets.api.widgets.localisation.OgemaLocale;
import de.iwes.widgets.html.selectiontree.SelectionItem;

class DbItem implements SelectionItem {

	private final DataRecorderReference ref;
	
	DbItem(DataRecorderReference ref) {
		this.ref = ref;
	}
	
	DataRecorderReference getDataRecorder() {
		return ref;
	}

	@Override
	public String id() {
		 // avoid Windows issues: the path is always specified with '/' as separator
		return ref.getPath().toString().replace('\\', '/');
	}

	@Override
	public String label(OgemaLocale arg0) {
		return "FendoDb: " + ref.getPath().toString();
	}
	
}
