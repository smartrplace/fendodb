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
