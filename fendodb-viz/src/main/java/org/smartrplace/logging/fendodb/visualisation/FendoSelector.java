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
package org.smartrplace.logging.fendodb.visualisation;

import org.smartrplace.logging.fendodb.DataRecorderReference;
import org.smartrplace.logging.fendodb.FendoDbFactory;

import de.iwes.widgets.api.widgets.WidgetPage;
import de.iwes.widgets.api.widgets.localisation.OgemaLocale;
import de.iwes.widgets.api.widgets.sessionmanagement.OgemaHttpRequest;
import de.iwes.widgets.html.form.dropdown.TemplateDropdown;
import de.iwes.widgets.template.DisplayTemplate;

class FendoSelector extends TemplateDropdown<DataRecorderReference> {
	
	private static final long serialVersionUID = 1L;
	private final FendoDbFactory factory;

	FendoSelector(final WidgetPage<?> page, final String id, final FendoDbFactory factory) {
		super(page,id);
		this.factory = factory;
		setTemplate(TEMPLATE);
	}
	
	@Override
	public void onGET(OgemaHttpRequest req) {
		update(factory.getAllInstances().values(), req);
	}

	private final static DisplayTemplate<DataRecorderReference> TEMPLATE = new DisplayTemplate<DataRecorderReference>() {
		
		@Override
		public String getLabel(DataRecorderReference arg0, OgemaLocale arg1) {
			return arg0.getPath().toString();
		}
		
		@Override
		public String getId(DataRecorderReference arg0) {
			return arg0.getPath().toString();
		}
	};
	
}
