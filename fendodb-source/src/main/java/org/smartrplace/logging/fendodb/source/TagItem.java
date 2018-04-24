package org.smartrplace.logging.fendodb.source;

import org.ogema.tools.resource.util.ResourceUtils;

import de.iwes.widgets.html.selectiontree.samples.SelectionItemImpl;

class TagItem extends SelectionItemImpl {

	private final String tag;
	
	public TagItem(String tag) {
		super(ResourceUtils.getValidResourceName(tag), tag);
		this.tag = tag;
	}

	String getTag() {
		return tag;
	}
	
}
