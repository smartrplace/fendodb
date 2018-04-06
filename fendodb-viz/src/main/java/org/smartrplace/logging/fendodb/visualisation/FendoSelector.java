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
