package org.smartrplace.logging.fendodb.source;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import org.smartrplace.logging.fendodb.FendoDbFactory;

import de.iwes.widgets.api.widgets.localisation.OgemaLocale;
import de.iwes.widgets.html.selectiontree.LinkingOption;
import de.iwes.widgets.html.selectiontree.SelectionItem;

class DbOption extends LinkingOption {

	private final FendoDbFactory factory;
	
	DbOption(FendoDbFactory factory) {
		this.factory = factory;
	}
	
	@Override
	public LinkingOption[] dependencies() {
		return null;
	}

	@Override
	public List<SelectionItem> getOptions(List<Collection<SelectionItem>> dependencies) {
		return factory.getAllInstances().values().stream()
			.map(ref -> new DbItem(ref))
			.collect(Collectors.toList());
	}

	@Override
	public String id() {
		return "dboption";
	}

	@Override
	public String label(OgemaLocale locale) {
		return "Select database";
	}
	
}
