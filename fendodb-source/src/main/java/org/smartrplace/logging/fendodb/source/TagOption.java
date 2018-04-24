package org.smartrplace.logging.fendodb.source;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.smartrplace.logging.fendodb.CloseableDataRecorder;
import org.smartrplace.logging.fendodb.DataRecorderReference;

import de.iwes.widgets.api.widgets.localisation.OgemaLocale;
import de.iwes.widgets.html.selectiontree.LinkingOption;
import de.iwes.widgets.html.selectiontree.SelectionItem;

class TagOption extends LinkingOption {

	private final LinkingOption[] dependencies;
	
	TagOption(DbOption dbOption) {
		this.dependencies = new LinkingOption[] {dbOption};
	}

	@Override
	public LinkingOption[] dependencies() {
		return dependencies.clone();
	}

	@Override
	public List<SelectionItem> getOptions(List<Collection<SelectionItem>> dependencies) {
		if (dependencies == null || dependencies.isEmpty())
			return Collections.emptyList();
		try {
			return dependencies.get(0).stream()
				.flatMap(item -> getProperties(((DbItem) item).getDataRecorder()))
				.distinct()
				.map(tag -> new TagItem(tag))
				.collect(Collectors.toList());
		} catch (UncheckedIOException e) {
			e.printStackTrace(); // TODO
			return Collections.emptyList();
		}
	}
	
	private static Stream<String> getProperties(final DataRecorderReference ref) {
		try (final CloseableDataRecorder rec = Utils.open(ref)) {
			if (rec == null)
				return Stream.empty();
			return rec.getAllProperties().keySet().stream();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
	
	@Override
	public String id() {
		return "dbTag";
	}

	@Override
	public String label(OgemaLocale locale) {
		return "Select tag";
	}
	
	
	
}
