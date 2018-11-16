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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalUnit;
import java.util.Arrays;
import java.util.Collections;

import org.ogema.core.application.ApplicationManager;
import org.osgi.framework.FrameworkUtil;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.smartrplace.logging.fendodb.CloseableDataRecorder;
import org.smartrplace.logging.fendodb.DataRecorderReference;
import org.smartrplace.logging.fendodb.FendoDbConfiguration;
import org.smartrplace.logging.fendodb.FendoDbConfigurationBuilder;
import org.smartrplace.logging.fendodb.FendoDbFactory;

import de.iwes.widgets.api.extended.WidgetData;
import de.iwes.widgets.api.extended.html.bricks.PageSnippet;
import de.iwes.widgets.api.extended.plus.SelectorTemplate;
import de.iwes.widgets.api.widgets.LazyWidgetPage;
import de.iwes.widgets.api.widgets.WidgetGroup;
import de.iwes.widgets.api.widgets.WidgetPage;
import de.iwes.widgets.api.widgets.dynamics.TriggeredAction;
import de.iwes.widgets.api.widgets.dynamics.TriggeringAction;
import de.iwes.widgets.api.widgets.html.StaticTable;
import de.iwes.widgets.api.widgets.sessionmanagement.OgemaHttpRequest;
import de.iwes.widgets.html.alert.Alert;
import de.iwes.widgets.html.buttonconfirm.ButtonConfirm;
import de.iwes.widgets.html.buttonconfirm.ButtonConfirmData;
import de.iwes.widgets.html.calendar.datepicker.Datepicker;
import de.iwes.widgets.html.form.button.Button;
import de.iwes.widgets.html.form.button.ButtonData;
import de.iwes.widgets.html.form.checkbox.Checkbox;
import de.iwes.widgets.html.form.dropdown.TemplateDropdown;
import de.iwes.widgets.html.form.label.Header;
import de.iwes.widgets.html.form.label.Label;
import de.iwes.widgets.html.form.textfield.TextField;
import de.iwes.widgets.html.form.textfield.ValueInputField;
import de.iwes.widgets.html.popup.Popup;

@Component(
		service=LazyWidgetPage.class,
		property= {
				LazyWidgetPage.BASE_URL + "=" + FendoVizConstants.URL_BASE, 
				LazyWidgetPage.RELATIVE_URL + "=overview.html",
				LazyWidgetPage.MENU_ENTRY + "=FendoDB overview"
		}
)
public class FendoDbOverviewPage implements LazyWidgetPage {
	
	@Reference
	private FendoDbFactory factory;

	@Override
	public void init(ApplicationManager appMan, WidgetPage<?> page) {
		new FendoDbOverviewPageInit(page, factory);
	}
	
	private static class FendoDbOverviewPageInit {
	
		private final WidgetPage<?> page;
		private final Header header;
		private final Alert alert;
		private final TemplateDropdown<DataRecorderReference> slotsSelector;
		private final Label readOnlyModeField;
		private final Label compatModeField;
		private final Label unitField;
		private final Label maxDaysField;
		private final Label maxSizeField;
		private final Label flushPeriodField;
		private final Label maxOpenFilesField;
		private final Label parseFolderOnInitField;
		private final Label reloadFoldersField;
		private final WidgetGroup dependentFields;
		private final ButtonConfirm closeDb;
	
		private final Popup newPopup;
		private final TextField newDbFolder;
	//	private final Header newPopupHeader;
		private final Checkbox newCompatModeField;
		private final TemplateDropdown<TemporalUnit> newUnitField;
		private final Checkbox newReadOnlyModeField;
		private final Checkbox newParseFolderOnInitField;
		private final ValueInputField<Integer> newMaxDaysField;
		private final ValueInputField<Integer> newMaxSizeField;
		private final ValueInputField<Long> newFlushPeriodField;
		private final ValueInputField<Integer> newMaxOpenFilesField;
		private final ValueInputField<Long> newReloadFoldersField;
		private final Button newDbSubmit;
		private final Button reloadDaysSubmit;
		private final Button newPopupTrigger;
	
		private final Popup copyPopup;
		private final TextField copyDbFolder;
		private final Checkbox copyCompatModeField;
		private final TemplateDropdown<TemporalUnit> copyUnitField;
		private final Checkbox copyReadOnlyModeField;
		private final ValueInputField<Integer> copyMaxDaysField;
		private final ValueInputField<Integer> copyMaxSizeField;
		private final ValueInputField<Long> copyFlushPeriodField;
		private final ValueInputField<Long> copyReloadFoldersField;
		private final ValueInputField<Integer> copyMaxOpenFilesField;
		private final Datepicker copyStartPicker;
		private final Datepicker copyEndPicker;
		private final Button copyDbSubmit;
		private final Button copyPopupTrigger;
	
		private final Popup updatePopup;
		private final Checkbox updateCompatModeField;
		private final TemplateDropdown<TemporalUnit> updateUnitField;
		private final Checkbox updateReadOnlyModeField;
		private final ValueInputField<Integer> updateMaxDaysField;
		private final ValueInputField<Integer> updateMaxSizeField;
		private final ValueInputField<Long> updateFlushPeriodField;
		private final ValueInputField<Integer> updateMaxOpenFilesField;
		private final ValueInputField<Long> updateReloadFoldersField;
		private final Button updateDbSubmit;
		private final Button updatePopupTrigger;
	
		@SuppressWarnings({ "serial", "deprecation" })
		FendoDbOverviewPageInit(final WidgetPage<?> page, final FendoDbFactory factory) {
			this.page = page;
			page.setTitle("FendoDB overview");
			this.header = new Header(page, "header", true);
			header.setDefaultText("FendoDB overview");
			header.setDefaultColor("blue");
			header.addDefaultStyle(WidgetData.TEXT_ALIGNMENT_CENTERED);
			this.alert = new Alert(page, "alert", "");
			alert.setDefaultVisibility(false);
			this.slotsSelector = new FendoSelector(page, "slotsSelector", factory);
			this.readOnlyModeField = new ConfigurationLabel(page, "readOnlyModeField", slotsSelector) {
	
				@Override
				String getValue(FendoDbConfiguration config) {
					return String.valueOf(config.isReadOnlyMode());
				}
			};
			this.compatModeField = new ConfigurationLabel(page, "configMode", slotsSelector) {
	
				@Override
				String getValue(FendoDbConfiguration config) {
					return String.valueOf(config.useCompatibilityMode());
				}
			};
			this.unitField  =new ConfigurationLabel(page, "unit", slotsSelector) {
	
				@Override
				String getValue(FendoDbConfiguration config) {
					return config.getFolderCreationTimeUnit().toString();
				}
	
			};
			this.flushPeriodField = new ConfigurationLabel(page, "flushPeriod", slotsSelector) {
	
				@Override
				String getValue(FendoDbConfiguration config) {
					return String.valueOf(getFlushPeriodSeconds(config)) + " s";
				}
			};
			this.reloadFoldersField = new ConfigurationLabel(page, "reloadDays", slotsSelector) {
				
				@Override
				String getValue(FendoDbConfiguration config) {
					return String.valueOf(getUpdateTimeMinutes(config.getReloadDaysInterval())) + " min";
				}
			};
			this.maxDaysField = new ConfigurationLabel(page, "maxDays", slotsSelector) {
	
				@Override
				String getValue(FendoDbConfiguration config) {
					return String.valueOf(config.getDataLifetimeInDays()) + " days";
				}
			};
			this.maxSizeField = new ConfigurationLabel(page, "maxSize", slotsSelector) {
	
				@Override
				String getValue(FendoDbConfiguration config) {
					return String.valueOf(config.getMaxDatabaseSize()) + " MB";
				}
			};
			this.maxOpenFilesField =  new ConfigurationLabel(page, "maxOpenFolders", slotsSelector) {
	
				@Override
				String getValue(FendoDbConfiguration config) {
					return String.valueOf(config.getMaxOpenFolders());
				}
			};
			this.parseFolderOnInitField = new ConfigurationLabel(page, "parseFolderOnInitField", slotsSelector) {
				
				@Override
				String getValue(FendoDbConfiguration config) {
					return String.valueOf(config.isReadFolders());
				}
			};
			this.closeDb = new ButtonConfirm(page, "buttonConfirm") {
	
				@Override
				public void onGET(OgemaHttpRequest req) {
					final DataRecorderReference item = slotsSelector.getSelectedItem(req);
					if (item == null) {
						disable(req);
						return;
					}
					final String slotsDataPath0 = FrameworkUtil.getBundle(getClass()).getBundleContext().getProperty("org.ogema.recordeddata.slotsdb.dbfolder");
					final String slotsDataPath = slotsDataPath0 != null ? slotsDataPath0 : "./data/slotsdb";
					final Path path = item.getPath();
					try {
						if (Files.isSameFile(path, Paths.get(slotsDataPath))) {
							disable(req);
							return;
						}
					} catch (NoSuchFileException e2) {
					} catch (IOException e1) {
						disable(req);
						return;
					}
					enable(req);
				}
	
				@Override
				public void onPOSTComplete(String data, OgemaHttpRequest req) {
					final DataRecorderReference item = slotsSelector.getSelectedItem(req);
					if (item == null)
						return;
					final String slotsDataPath0 = FrameworkUtil.getBundle(getClass()).getBundleContext().getProperty("org.ogema.recordeddata.slotsdb.dbfolder");
					final String slotsDataPath = slotsDataPath0 != null ? slotsDataPath0 : "./data/slotsdb";
					final Path path = item.getPath();
					try {
						if (Files.isSameFile(path, Paths.get(slotsDataPath)))
							return;
					} catch (IOException e1) {
						return;
					}
					try {
						item.getDataRecorder().close();
						alert.showAlert("Database closed: " + item.getPath(), true, req);
					} catch (Exception e) {
						alert.showAlert("Closing failed: " + e, false, req);
					}
				}
	
			};
			closeDb.setDefaultText("Close");
			closeDb.setDefaultCancelBtnMsg("Cancel");
			closeDb.setDefaultConfirmBtnMsg("Close");
			closeDb.setDefaultConfirmMsg("Do you really want to close the database?");
			closeDb.setDefaultConfirmPopupTitle("Confirm closing of the database.");
			closeDb.addDefaultStyle(ButtonData.BOOTSTRAP_ORANGE);
			closeDb.addDefaultStyle(ButtonConfirmData.CANCEL_LIGHT_BLUE);
			closeDb.addDefaultStyle(ButtonConfirmData.CONFIRM_ORANGE);	
			this.newDbFolder = new TextField(page, "newDbFolder");
			this.newCompatModeField = new Checkbox(page, "newCompatModeField");
			newCompatModeField.setDefaultList(Collections.singletonMap("", false));
			this.newUnitField = new UnitSelector(page, "newUnitSelector") {
	
				@Override
				public void onGET(OgemaHttpRequest req) {
					final boolean compatMode = newCompatModeField.getCheckboxList(req).get("");
					if (compatMode) {
						selectItem(ChronoUnit.DAYS, req);
						disable(req);
					} else
						enable(req);
				}
	
			};
			this.newReadOnlyModeField = new Checkbox(page, "newReadOnlyModeField");
			newReadOnlyModeField.setDefaultList(Collections.singletonMap("", false));
			this.newParseFolderOnInitField = new Checkbox(page, "newParseFolderOnInitField");
			newParseFolderOnInitField.setDefaultList(Collections.singletonMap("", true));
			this.newMaxDaysField = new ValueInputField<>(page, "newMaxDaysField", Integer.class);
			newMaxDaysField.setDefaultLowerBound(0);
			newMaxDaysField.setDefaultNumericalValue(0);
			this.newMaxSizeField = new ValueInputField<>(page, "newMaxSizeField", Integer.class);
			newMaxSizeField.setDefaultLowerBound(0);
			newMaxSizeField.setDefaultNumericalValue(0);
			this.newFlushPeriodField = new ValueInputField<>(page, "newFlushPeriodField", Long.class);
			newFlushPeriodField.setDefaultLowerBound(0);
			newFlushPeriodField.setDefaultNumericalValue(10L);
			this.newReloadFoldersField = new ValueInputField<>(page, "newReloadFoldersField", Long.class);
			newReloadFoldersField.setDefaultLowerBound(0);
			newReloadFoldersField.setDefaultNumericalValue(0L);
			this.newMaxOpenFilesField = new ValueInputField<>(page, "newMaxOpenFilesField", Integer.class);
			newMaxOpenFilesField.setDefaultLowerBound(8);
			newMaxOpenFilesField.setDefaultNumericalValue(512);
			this.newDbSubmit = new Button(page, "newDbSubmit", "Submit") {
	
				@Override
				public void onPOSTComplete(String data, OgemaHttpRequest req) {
					final String path = newDbFolder.getValue(req);
					if (path == null) {
						alert.showAlert("No database path selected", false, req);
						return;
					}
					final Path newPath = Paths.get(path).normalize();
					if (slotsSelector.getItems(req).stream()
						.filter(db -> db.getPath().toString().equals(newPath.toString()))
						.findAny().isPresent()) {
						alert.showAlert("A database for the path " + path + " already exists", false, req);
						return;
					}
					final boolean parseOnInit = newParseFolderOnInitField.getCheckboxList(req).get("");
					final boolean readOnlyMode = newReadOnlyModeField.getCheckboxList(req).get("");
					final TemporalUnit unit = newUnitField.getSelectedItem(req);
					final Integer maxDays = newMaxDaysField.getNumericalValue(req);
					final Long flushPeriod = newFlushPeriodField.getNumericalValue(req);
					final Long reloadFoldersPeriod = newReloadFoldersField.getNumericalValue(req);
					final Integer maxOpenFiles = newMaxOpenFilesField.getNumericalValue(req);
					final Integer maxSize = newMaxSizeField.getNumericalValue(req);
					if (unit == null || maxDays == null || flushPeriod == null || maxOpenFiles == null || maxSize == null || reloadFoldersPeriod == null)
						return;
					final boolean useCompatMode = newCompatModeField.getCheckboxList(req).get("");
					if (useCompatMode && !ChronoUnit.DAYS.equals(unit)) {
						alert.showAlert("Compatibility mode is only possible with temporal unit DAYS.", false, req);
						return;
					}
					final FendoDbConfiguration config = FendoDbConfigurationBuilder.getInstance()
						.setDataLifetimeInDays(maxDays)
						.setFlushPeriod(flushPeriod * 1000)
						.setReloadDaysInterval(reloadFoldersPeriod * 60000) 
						.setMaxOpenFolders(maxOpenFiles)
						.setParseFoldersOnInit(parseOnInit)
						.setMaxDatabaseSize(maxSize)
						.setTemporalUnit(unit)
						.setUseCompatibilityMode(useCompatMode)
						.setReadOnlyMode(readOnlyMode)
						.build();
					try {
						final CloseableDataRecorder instance = factory.getInstance(newPath, config);
						alert.showAlert("New database instance created: " + instance.getPath(), true, req);
					} catch (IOException e) {
						final String msg0 = e.getMessage();
						final String msg = msg0 != null ? msg0 : e.toString();
						alert.showAlert("Failed to create new instance: " + msg, false, req);
					}
				}
	
			};
	
			this.newPopup = new Popup(page, "newPopup", true);
	//		this.newPopupHeader = new Header(page, "newPopupHeader", true);
	//		newPopupHeader.setDefaultText("Create a new SlotsDb instance");
	//		newPopupHeader.addDefaultStyle(WidgetData.TEXT_ALIGNMENT_CENTERED);
	//		newPopupHeader.setDefaultColor("blue");
			this.newPopupTrigger = new Button(page, "newPopupTrigger", "Open dialog");
			
			this.reloadDaysSubmit = new Button(page, "reloadDaysSubmit", "Reload days") {
				
				public void onGET(OgemaHttpRequest req) {
					final DataRecorderReference recorder = slotsSelector.getSelectedItem(req);
					if (recorder == null)
						disable(req);
					else
						enable(req);
				}
				
				public void onPOSTComplete(String data, OgemaHttpRequest req) {
					final DataRecorderReference ref = slotsSelector.getSelectedItem(req);
					if (ref == null)
						return;
					try (final CloseableDataRecorder recorder = ref.getDataRecorder()) {
						recorder.reloadDays();
						alert.showAlert("Days folder list reloaded", true, req);
					} catch (IOException e) {
						alert.showAlert("Failed to reload folders list: " + e, false, req);
					}
				}
				
			};
			
			this.copyDbFolder = new TextField(page, "copyDbFolder");
			this.copyCompatModeField = new Checkbox(page, "copyCompatModeField") {
	
				public void onGET(OgemaHttpRequest req) {
					final DataRecorderReference recorder = slotsSelector.getSelectedItem(req);
					boolean compat;
					if (recorder == null)
						compat = false;
					else {
						compat = recorder.getConfiguration().useCompatibilityMode();
					}
					setCheckboxList(Collections.singletonMap("", compat), req);
				}
	
			};
			copyCompatModeField.setDefaultList(Collections.singletonMap("", false));
			this.copyUnitField = new UnitSelector(page, "copyUnitSelector") {
	
				@Override
				public void onGET(OgemaHttpRequest req) {
					final boolean compatMode = copyCompatModeField.getCheckboxList(req).get("");
					final DataRecorderReference recorder = slotsSelector.getSelectedItem(req);
					final TemporalUnit unit;
					if (recorder == null || compatMode) {
						unit = ChronoUnit.DAYS;
					} else {
						unit = recorder.getConfiguration().getFolderCreationTimeUnit();
					}
					selectItem(unit, req);
					if (compatMode)
						disable(req);
					else
						enable(req);
				}
	
			};
			this.copyReadOnlyModeField = new Checkbox(page, "copyReadOnlyModeField");
			copyReadOnlyModeField.setDefaultList(Collections.singletonMap("", false));
			this.copyMaxDaysField = new ValueInputField<Integer>(page, "copyMaxDaysField", Integer.class) {
	
				public void onGET(OgemaHttpRequest req) {
					final DataRecorderReference recorder = slotsSelector.getSelectedItem(req);
					if (recorder != null) {
						setNumericalValue(recorder.getConfiguration().getDataLifetimeInDays(), req);
					}
				}
	
			};
			copyMaxDaysField.setDefaultLowerBound(0);
			copyMaxDaysField.setDefaultNumericalValue(0);
			this.copyMaxSizeField = new ValueInputField<Integer>(page, "copyMaxSizeField", Integer.class) {
	
				public void onGET(OgemaHttpRequest req) {
					final DataRecorderReference recorder = slotsSelector.getSelectedItem(req);
					if (recorder != null) {
						setNumericalValue(recorder.getConfiguration().getMaxDatabaseSize(), req);
					}
				}
	
			};
			copyMaxSizeField.setDefaultLowerBound(0);
			copyMaxSizeField.setDefaultNumericalValue(0);
			this.copyFlushPeriodField = new ValueInputField<Long>(page, "copyFlushPeriodField", Long.class) {
	
				public void onGET(OgemaHttpRequest req) {
					final DataRecorderReference recorder = slotsSelector.getSelectedItem(req);
					if (recorder != null) {
						setNumericalValue(getFlushPeriodSeconds(recorder.getConfiguration()), req);
					}
				}
	
			};
			copyFlushPeriodField.setDefaultLowerBound(0);
			copyFlushPeriodField.setDefaultNumericalValue(10L);
			this.copyReloadFoldersField = new ValueInputField<Long>(page, "copyReloadFoldersField", Long.class) {
				
				public void onGET(OgemaHttpRequest req) {
					final DataRecorderReference recorder = slotsSelector.getSelectedItem(req);
					if (recorder != null) {
						setNumericalValue(getUpdateTimeMinutes(recorder.getConfiguration().getReloadDaysInterval()), req);
					}
				}
	
			};
			copyReloadFoldersField.setDefaultLowerBound(0);
			copyReloadFoldersField.setDefaultNumericalValue(10L);
			this.copyMaxOpenFilesField = new ValueInputField<Integer>(page, "copyMaxOpenFilesField", Integer.class) {
	
				public void onGET(OgemaHttpRequest req) {
					final DataRecorderReference recorder = slotsSelector.getSelectedItem(req);
					if (recorder != null) {
						setNumericalValue(recorder.getConfiguration().getMaxOpenFolders(), req);
					}
				}
	
			};
			copyMaxOpenFilesField.setDefaultLowerBound(8);
			copyMaxOpenFilesField.setDefaultNumericalValue(512);
			this.copyStartPicker = new Datepicker(page, "copyStartPicker");
			copyStartPicker.setDefaultDate("1970-01-01 00:00:00"); // timezone?
			this.copyEndPicker = new Datepicker(page, "copyEndPicker");
			copyEndPicker.setDefaultDate("2020-01-01 00:00:00");
	
			this.copyDbSubmit = new Button(page, "copyDbSubmit", "Submit") {
	
				@Override
				public void onPOSTComplete(String data, OgemaHttpRequest req) {
					final DataRecorderReference recorder = slotsSelector.getSelectedItem(req);
					if (recorder == null)
						return;
					final String path = copyDbFolder.getValue(req);
					if (path == null) {
						alert.showAlert("No database path selected", false, req);
						return;
					}
					final Path copyPath = Paths.get(path).normalize();
					if (slotsSelector.getItems(req).stream()
						.filter(db -> db.getPath().toString().equalsIgnoreCase(copyPath.toString()))
						.findAny().isPresent()) {
						alert.showAlert("A database for the path " + path + " already exists", false, req);
						return;
					}
					final boolean readOnlyMode = copyReadOnlyModeField.getCheckboxList(req).get("");
					final TemporalUnit unit = copyUnitField.getSelectedItem(req);
					final Integer maxDays = copyMaxDaysField.getNumericalValue(req);
					final Long flushPeriod = copyFlushPeriodField.getNumericalValue(req);
					final Long reloadFoldersPeriod = copyReloadFoldersField.getNumericalValue(req);
					final Integer maxOpenFiles = copyMaxOpenFilesField.getNumericalValue(req);
					final Integer maxSize = copyMaxSizeField.getNumericalValue(req);
					if (unit == null || maxDays == null || flushPeriod == null || maxOpenFiles == null || maxSize == null || reloadFoldersPeriod == null)
						return;
					final boolean useCompatMode = copyCompatModeField.getCheckboxList(req).get("");
					if (useCompatMode && !ChronoUnit.DAYS.equals(unit)) {
						alert.showAlert("Compatibility mode is only possible with temporal unit DAYS.", false, req);
						return;
					}
					final FendoDbConfiguration config = FendoDbConfigurationBuilder.getInstance()
						.setDataLifetimeInDays(maxDays)
						.setFlushPeriod(flushPeriod * 1000)
						.setReloadDaysInterval(reloadFoldersPeriod * 60000)
						.setMaxOpenFolders(maxOpenFiles)
						.setParseFoldersOnInit(true)
						.setMaxDatabaseSize(maxSize)
						.setTemporalUnit(unit)
						.setUseCompatibilityMode(useCompatMode)
						.setReadOnlyMode(readOnlyMode)
						.build();
					final long start = copyStartPicker.getDateLong(req);
					final long end = copyEndPicker.getDateLong(req);
					try (final CloseableDataRecorder instance = recorder.getDataRecorder()) {
						final DataRecorderReference ref = instance.copy(copyPath, config, start, end);
						alert.showAlert("new database instance created: " + ref.getPath(), true, req);
					} catch (Exception e) {
						final String msg0 = e.getMessage();
						final String msg = msg0 != null ? msg0 : e.toString();
						alert.showAlert("Failed to create new instance: " + msg, false, req);
					}
				}
	
			};
	
			this.copyPopup = new Popup(page, "copyPopup", true);
			this.copyPopupTrigger = new Button(page, "copyPopupTrigger", "Open dialog") {
	
				public void onGET(OgemaHttpRequest req) {
					final DataRecorderReference recorder = slotsSelector.getSelectedItem(req);
					 if (recorder == null)
						disable(req);
					else
						enable(req);
				}
	
			};
	
			this.updateCompatModeField = new Checkbox(page, "updateCompatModeField") {
	
				public void onGET(OgemaHttpRequest req) {
					final DataRecorderReference recorder = slotsSelector.getSelectedItem(req);
					final boolean compat;
					if (recorder == null)
						compat = false;
					else
						compat = recorder.getConfiguration().useCompatibilityMode();
					setCheckboxList(Collections.singletonMap("", compat), req);
				}
	
			};
			updateCompatModeField.setDefaultList(Collections.singletonMap("", false));
			this.updateUnitField = new UnitSelector(page, "updateUnitSelector") {
	
				@Override
				public void onGET(OgemaHttpRequest req) {
					final boolean compatMode = updateCompatModeField.getCheckboxList(req).get("");
					final DataRecorderReference recorder = slotsSelector.getSelectedItem(req);
					final TemporalUnit unit;
					if (recorder == null || compatMode) {
						unit = ChronoUnit.DAYS;
					} else
						unit = recorder.getConfiguration().getFolderCreationTimeUnit();
					selectItem(unit, req);
					if (compatMode)
						disable(req);
					else
						enable(req);
				}
	
			};
			this.updateReadOnlyModeField = new Checkbox(page, "updateReadOnlyModeField");
			updateReadOnlyModeField.setDefaultList(Collections.singletonMap("", false));
			this.updateMaxDaysField = new ValueInputField<Integer>(page, "updateMaxDaysField", Integer.class) {
	
				public void onGET(OgemaHttpRequest req) {
					final DataRecorderReference recorder = slotsSelector.getSelectedItem(req);
					if (recorder != null) {
						setNumericalValue(recorder.getConfiguration().getDataLifetimeInDays(), req);
					}
				}
	
			};
			updateMaxDaysField.setDefaultLowerBound(0);
			updateMaxDaysField.setDefaultNumericalValue(0);
			this.updateMaxSizeField = new ValueInputField<Integer>(page, "updateMaxSizeField", Integer.class) {
	
				public void onGET(OgemaHttpRequest req) {
					final DataRecorderReference recorder = slotsSelector.getSelectedItem(req);
					if (recorder != null) {
						setNumericalValue(recorder.getConfiguration().getMaxDatabaseSize(), req);
					}
				}
	
			};
			updateMaxSizeField.setDefaultLowerBound(0);
			updateMaxSizeField.setDefaultNumericalValue(0);
			this.updateFlushPeriodField = new ValueInputField<Long>(page, "updateFlushPeriodField", Long.class) {
	
				public void onGET(OgemaHttpRequest req) {
					final DataRecorderReference recorder = slotsSelector.getSelectedItem(req);
					if (recorder != null) {
						setNumericalValue(getFlushPeriodSeconds(recorder.getConfiguration()), req);
					}
				}
	
			};
			updateFlushPeriodField.setDefaultLowerBound(0);
			updateFlushPeriodField.setDefaultNumericalValue(10L);
			this.updateReloadFoldersField = new ValueInputField<Long>(page, "updateReloadFoldersField", Long.class) {
				
				public void onGET(OgemaHttpRequest req) {
					final DataRecorderReference recorder = slotsSelector.getSelectedItem(req);
					if (recorder != null) {
						setNumericalValue(getUpdateTimeMinutes(recorder.getConfiguration().getReloadDaysInterval()), req);
					}
				}
	
			};
			updateReloadFoldersField.setDefaultLowerBound(0);
			updateReloadFoldersField.setDefaultNumericalValue(10L);
			this.updateMaxOpenFilesField = new ValueInputField<Integer>(page, "updateMaxOpenFilesField", Integer.class) {
	
				public void onGET(OgemaHttpRequest req) {
					final DataRecorderReference recorder = slotsSelector.getSelectedItem(req);
					if (recorder != null) {
						setNumericalValue(recorder.getConfiguration().getMaxOpenFolders(), req);
					}
				}
	
			};
			updateMaxOpenFilesField.setDefaultLowerBound(8);
			updateMaxOpenFilesField.setDefaultNumericalValue(512);
			this.updateDbSubmit = new Button(page, "updateDbSubmit", "Submit") {
	
				@Override
				public void onPOSTComplete(String data, OgemaHttpRequest req) {
					final DataRecorderReference recorder = slotsSelector.getSelectedItem(req);
					if (recorder == null)
						return;
					final boolean readOnlyMode = updateReadOnlyModeField.getCheckboxList(req).get("");
					final TemporalUnit unit = updateUnitField.getSelectedItem(req);
					final Integer maxDays = updateMaxDaysField.getNumericalValue(req);
					final Long flushPeriod = updateFlushPeriodField.getNumericalValue(req);
					final Long reloadFodlersPeriod = updateReloadFoldersField.getNumericalValue(req);
					final Integer maxOpenFiles = updateMaxOpenFilesField.getNumericalValue(req);
					final Integer maxSize = updateMaxSizeField.getNumericalValue(req);
					if (unit == null || maxDays == null || flushPeriod == null || maxOpenFiles == null || maxSize == null || reloadFodlersPeriod == null)
						return;
					final boolean useCompatMode = updateCompatModeField.getCheckboxList(req).get("");
					if (useCompatMode && !ChronoUnit.DAYS.equals(unit)) {
						alert.showAlert("Compatibility mode is only possible with temporal unit DAYS.", false, req);
						return;
					}
					final FendoDbConfiguration config = FendoDbConfigurationBuilder.getInstance(recorder.getConfiguration())
						.setDataLifetimeInDays(maxDays)
						.setFlushPeriod(flushPeriod * 1000)
						.setReloadDaysInterval(reloadFodlersPeriod * 60000)
						.setMaxOpenFolders(maxOpenFiles)
						.setParseFoldersOnInit(true) // FIXME?
						.setMaxDatabaseSize(maxSize)
						.setTemporalUnit(unit)
						.setUseCompatibilityMode(useCompatMode)
						.setReadOnlyMode(readOnlyMode)
						.build();
					try (final CloseableDataRecorder rec = recorder.getDataRecorder()) {
						final DataRecorderReference instance = rec.updateConfiguration(config);
						alert.showAlert("Database configuration updated: " + instance.getPath(), true, req);
					} catch (Exception e) {
						final String msg0 = e.getMessage();
						final String msg = msg0 != null ? msg0 : e.toString();
						alert.showAlert("Failed to update configuration: " + msg, false, req);
					}
				}
	
			};
	
			this.updatePopup = new Popup(page, "updatePopup", true);
			this.updatePopupTrigger = new Button(page, "updatePopupTrigger", "Open dialog") {
	
				public void onGET(OgemaHttpRequest req) {
					final DataRecorderReference recorder = slotsSelector.getSelectedItem(req);
					 if (recorder == null)
						disable(req);
					else
						enable(req);
				}
	
			};
	
			this.dependentFields = page.registerWidgetGroup("dependentFields", Arrays.asList(
					readOnlyModeField, compatModeField, unitField, flushPeriodField, reloadFoldersField, maxDaysField, maxSizeField, maxOpenFilesField, parseFolderOnInitField, closeDb, reloadDaysSubmit,
					copyReadOnlyModeField, copyCompatModeField, copyUnitField, copyFlushPeriodField, copyReloadFoldersField, copyMaxDaysField, copyMaxSizeField, copyMaxOpenFilesField,
					updateReadOnlyModeField, updateCompatModeField, updateUnitField, updateFlushPeriodField, updateReloadFoldersField, updateMaxDaysField, updateMaxSizeField, updateMaxOpenFilesField
				));
	
			buildPage();
			setDependencies();
		}
	
		private final void buildPage() {
			final PageSnippet body = new PageSnippet(page, "newPopupBody", true);
			int row = 0;
			body.append(new StaticTable(11, 2)
				.setContent(row, 0, "Database path relative to rundir").setContent(row++, 1, newDbFolder)
				.setContent(row, 0, "Open in read only mode?").setContent(row++, 1, newReadOnlyModeField)
				.setContent(row, 0, "Use compatibility mode?").setContent(row++, 1, newCompatModeField)
				.setContent(row, 0, "Parse folders on init?").setContent(row++, 1, newParseFolderOnInitField)
				.setContent(row, 0, "Folder time interval").setContent(row++, 1, newUnitField)
				.setContent(row, 0, "Max data lifetime (days)").setContent(row++, 1, newMaxDaysField)
				.setContent(row, 0, "Max data size (MB)").setContent(row++, 1, newMaxSizeField)
				.setContent(row, 0, "Flush period (s)").setContent(row++, 1, newFlushPeriodField)
				.setContent(row, 0, "Folders reloading (min)").setContent(row++, 1, newReloadFoldersField)
				.setContent(row, 0, "Max nr open files").setContent(row++, 1, newMaxOpenFilesField)
														.setContent(row++, 1, newDbSubmit)
			, null);
			newPopup.setTitle("Create a new FendoDB instance", null);
			newPopup.setBody(body, null);
	
			final PageSnippet copyBody = new PageSnippet(page, "copyPopupBody", true);
			row = 0;
			copyBody.append(new StaticTable(12, 2)
				.setContent(row, 0, "Database path relative to rundir").setContent(row++, 1, copyDbFolder)
				.setContent(row, 0, "Open in read only mode?").setContent(row++, 1, copyReadOnlyModeField)
				.setContent(row, 0, "Use compatibility mode?").setContent(row++, 1, copyCompatModeField)
				.setContent(row, 0, "Folder time interval").setContent(row++, 1, copyUnitField)
				.setContent(row, 0, "Max data lifetime (days)").setContent(row++, 1, copyMaxDaysField)
				.setContent(row, 0, "Max data size (MB)").setContent(row++, 1, copyMaxSizeField)
				.setContent(row, 0, "Flush period (s)").setContent(row++, 1, copyFlushPeriodField)
				.setContent(row, 0, "Reload folders period (min)").setContent(row++, 1, copyReloadFoldersField)
				.setContent(row, 0, "Max nr open files").setContent(row++, 1, copyMaxOpenFilesField)
				.setContent(row, 0, "Copy start time").setContent(row++, 1, copyStartPicker)
				.setContent(row, 0, "Copy end time").setContent(row++, 1, copyEndPicker)
														.setContent(row++, 1, copyDbSubmit)
			, null);
			copyPopup.setTitle("Copy FendoDB instance", null);
			copyPopup.setBody(copyBody, null);
	
			final PageSnippet updateBody = new PageSnippet(page, "updatePopupBody", true);
			row = 0;
			updateBody.append(new StaticTable(9, 2)
				.setContent(row, 0, "Open in read only mode?").setContent(row++, 1, updateReadOnlyModeField)
				.setContent(row, 0, "Use compatibility mode?").setContent(row++, 1, updateCompatModeField)
				.setContent(row, 0, "Folder time interval").setContent(row++, 1, updateUnitField)
				.setContent(row, 0, "Max data lifetime (days)").setContent(row++, 1, updateMaxDaysField)
				.setContent(row, 0, "Max data size (MB)").setContent(row++, 1, updateMaxSizeField)
				.setContent(row, 0, "Flush period (s)").setContent(row++, 1, updateFlushPeriodField)
				.setContent(row, 0, "Reload folders period (min)").setContent(row++, 1, updateReloadFoldersField)
				.setContent(row, 0, "Max nr open files").setContent(row++, 1, updateMaxOpenFilesField)
														.setContent(row++, 1, updateDbSubmit)
			, null);
			updatePopup.setTitle("Update FendoDB instance", null);
			updatePopup.setBody(updateBody, null);
	
			row = 0;
			page.append(header).linebreak()
				.append(alert)
				.append(new StaticTable(15, 2, new int[] {3,3})
						.setContent(row, 0, "Select FendoDB instance").setContent(row++, 1, slotsSelector)
						.setContent(row, 0, "Open in read only mode?").setContent(row++, 1, readOnlyModeField)
						.setContent(row, 0, "Use compatibility mode?").setContent(row++, 1, compatModeField)
						.setContent(row, 0, "Time unit").setContent(row++, 1, unitField)
						.setContent(row, 0, "Flush period").setContent(row++, 1, flushPeriodField)
						.setContent(row, 0, "Max data lifetime").setContent(row++, 1, maxDaysField)
						.setContent(row, 0, "Max db size").setContent(row++, 1, maxSizeField)
						.setContent(row, 0, "Max open files").setContent(row++, 1, maxOpenFilesField)
						.setContent(row, 0, "Parse folders on init").setContent(row++, 1, parseFolderOnInitField)
						.setContent(row, 0, "Reload days interval").setContent(row++, 1, reloadFoldersField)
						.setContent(row, 0, "Update settings").setContent(row++, 1, updatePopupTrigger)
						.setContent(row, 0, "Reload days folders").setContent(row++, 1, reloadDaysSubmit)
						.setContent(row, 0, "Close instance").setContent(row++, 1, closeDb)
						.setContent(row, 0, "Copy instance").setContent(row++, 1, copyPopupTrigger)
						.setContent(row, 0, "Create a new instance").setContent(row++, 1, newPopupTrigger)
	
			).linebreak().append(newPopup).linebreak().append(copyPopup).linebreak().append(updatePopup);
	
		}
	
		private final void setDependencies() {
			slotsSelector.triggerAction(dependentFields, TriggeringAction.POST_REQUEST, TriggeredAction.GET_REQUEST);
			closeDb.triggerAction(alert, TriggeringAction.POST_REQUEST, TriggeredAction.GET_REQUEST);
			closeDb.triggerAction(slotsSelector, TriggeringAction.POST_REQUEST, TriggeredAction.GET_REQUEST);
			closeDb.triggerAction(dependentFields, TriggeringAction.POST_REQUEST, TriggeredAction.GET_REQUEST, 1);
			newDbSubmit.triggerAction(slotsSelector, TriggeringAction.POST_REQUEST, TriggeredAction.GET_REQUEST);
			newDbSubmit.triggerAction(dependentFields, TriggeringAction.POST_REQUEST, TriggeredAction.GET_REQUEST, 1);
			newDbSubmit.triggerAction(alert, TriggeringAction.POST_REQUEST, TriggeredAction.GET_REQUEST);
			newDbSubmit.triggerAction(newPopup, TriggeringAction.POST_REQUEST, TriggeredAction.HIDE_WIDGET);
			newPopupTrigger.triggerAction(newPopup, TriggeringAction.POST_REQUEST, TriggeredAction.SHOW_WIDGET);
			newCompatModeField.triggerAction(newUnitField, TriggeringAction.POST_REQUEST, TriggeredAction.GET_REQUEST);
	
			copyDbSubmit.triggerAction(slotsSelector, TriggeringAction.POST_REQUEST, TriggeredAction.GET_REQUEST);
			copyDbSubmit.triggerAction(dependentFields, TriggeringAction.POST_REQUEST, TriggeredAction.GET_REQUEST, 1);
			copyDbSubmit.triggerAction(alert, TriggeringAction.POST_REQUEST, TriggeredAction.GET_REQUEST);
			copyDbSubmit.triggerAction(copyPopup, TriggeringAction.POST_REQUEST, TriggeredAction.HIDE_WIDGET);
			copyPopupTrigger.triggerAction(copyPopup, TriggeringAction.POST_REQUEST, TriggeredAction.SHOW_WIDGET);
			copyCompatModeField.triggerAction(copyUnitField, TriggeringAction.POST_REQUEST, TriggeredAction.GET_REQUEST);
	
			updateDbSubmit.triggerAction(slotsSelector, TriggeringAction.POST_REQUEST, TriggeredAction.GET_REQUEST);
			updateDbSubmit.triggerAction(dependentFields, TriggeringAction.POST_REQUEST, TriggeredAction.GET_REQUEST, 1);
			updateDbSubmit.triggerAction(alert, TriggeringAction.POST_REQUEST, TriggeredAction.GET_REQUEST);
			updateDbSubmit.triggerAction(updatePopup, TriggeringAction.POST_REQUEST, TriggeredAction.HIDE_WIDGET);
			updatePopupTrigger.triggerAction(updatePopup, TriggeringAction.POST_REQUEST, TriggeredAction.SHOW_WIDGET);
			updateCompatModeField.triggerAction(updateUnitField, TriggeringAction.POST_REQUEST, TriggeredAction.GET_REQUEST);
			
			reloadDaysSubmit.triggerAction(alert, TriggeringAction.POST_REQUEST, TriggeredAction.GET_REQUEST);
		}
	
		private final static long getFlushPeriodSeconds(final FendoDbConfiguration config) {
			return getUpdateTimeSeconds(config.getFlushPeriod());
		}
		
		private final static long getUpdateTimeSeconds(long in) {
			if (in <= 0)
				in = 0;
			final long outS;
			if (in <= 0)
				outS = 0;
			else if (in < 1000)
				outS = 1;
			else
				outS = in/1000;
			return outS;
		}
		
		private final static long getUpdateTimeMinutes(long in) {
			if (in <= 0)
				in = 0;
			final long outS;
			if (in <= 0)
				outS = 0;
			else if (in < 60000)
				outS = 1;
			else
				outS = in/60000;
			return outS;
		}
		
		@SuppressWarnings("serial")
		private static abstract class ConfigurationLabel extends Label {
	
			private final SelectorTemplate<DataRecorderReference> slotsSelector;
	
			ConfigurationLabel(WidgetPage<?> page, String id, SelectorTemplate<DataRecorderReference> slotsSelector) {
				super(page, id);
				this.slotsSelector = slotsSelector;
			}
	
			@Override
			public void onGET(OgemaHttpRequest req) {
				final DataRecorderReference instance = slotsSelector.getSelectedItem(req);
				if (instance == null) {
					setText("", req);
					return;
				}
				try (final CloseableDataRecorder slots = instance.getDataRecorder()) {
					setText(getValue(slots.getConfiguration()), req);
				} catch (IOException e) {
					setText("", req);
				}
			}
	
			abstract String getValue(final FendoDbConfiguration config);
	
		}

	}
}
