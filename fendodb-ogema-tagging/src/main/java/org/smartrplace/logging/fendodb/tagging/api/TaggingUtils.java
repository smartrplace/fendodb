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
package org.smartrplace.logging.fendodb.tagging.api;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.ogema.core.model.Resource;
import org.ogema.model.actors.Actor;
import org.ogema.model.devices.buildingtechnology.ElectricLight;
import org.ogema.model.devices.buildingtechnology.Thermostat;
import org.ogema.model.devices.generators.CombinedHeatAndPowerGenerator;
import org.ogema.model.devices.generators.HeatGenerator;
import org.ogema.model.devices.generators.HeatPump;
import org.ogema.model.devices.generators.PVPlant;
import org.ogema.model.devices.sensoractordevices.MultiSwitchBox;
import org.ogema.model.devices.sensoractordevices.SingleSwitchBox;
import org.ogema.model.devices.storage.ElectricityStorage;
import org.ogema.model.devices.storage.EnergyStorage;
import org.ogema.model.devices.vehicles.ElectricVehicle;
import org.ogema.model.devices.whitegoods.CoolingDevice;
import org.ogema.model.locations.Building;
import org.ogema.model.locations.Room;
import org.ogema.model.metering.ElectricityMeter;
import org.ogema.model.metering.GenericMeter;
import org.ogema.model.metering.HeatMeter;
import org.ogema.model.prototypes.PhysicalElement;
import org.ogema.model.sensors.DoorWindowSensor;
import org.ogema.model.sensors.ElectricCurrentSensor;
import org.ogema.model.sensors.ElectricVoltageSensor;
import org.ogema.model.sensors.EnergyAccumulatedSensor;
import org.ogema.model.sensors.HumiditySensor;
import org.ogema.model.sensors.MotionSensor;
import org.ogema.model.sensors.OccupancySensor;
import org.ogema.model.sensors.PowerSensor;
import org.ogema.model.sensors.Sensor;
import org.ogema.model.sensors.TemperatureSensor;
import org.ogema.model.targetranges.TargetRange;
import org.smartrplace.logging.fendodb.CloseableDataRecorder;

public class TaggingUtils {

	// A set of often-used device types

	@SuppressWarnings("unchecked")
	private static final Class<? extends PhysicalElement>[] SPECIFIC_DEVICE_TYPES = new Class[] {
		 Thermostat.class,
		 TemperatureSensor.class,
		 HumiditySensor.class,
		 PowerSensor.class,
		 ElectricVehicle.class,
		 ElectricityStorage.class,
		 EnergyStorage.class,
		 ElectricCurrentSensor.class,
		 ElectricVoltageSensor.class,
		 EnergyAccumulatedSensor.class,
		 SingleSwitchBox.class,
		 MultiSwitchBox.class,
		 OccupancySensor.class,
		 MotionSensor.class,
		 CoolingDevice.class,
		 CombinedHeatAndPowerGenerator.class,
		 PVPlant.class,
		 HeatPump.class,
		 ElectricityMeter.class,
		 DoorWindowSensor.class,
		 ElectricLight.class,
		 GenericMeter.class
	};

	@SuppressWarnings("unchecked")
	private static final Class<? extends PhysicalElement>[] HEATING_TYPES = new Class[] {
			 Thermostat.class,
			 TemperatureSensor.class,
			 HumiditySensor.class,
			 HeatGenerator.class,
			 EnergyAccumulatedSensor.class,
			 OccupancySensor.class,
			 CombinedHeatAndPowerGenerator.class,
			 HeatPump.class,
			 HeatMeter.class,
			 DoorWindowSensor.class
		};

	@SuppressWarnings("unchecked")
	private static final Class<? extends PhysicalElement>[] ELECTRICITY_TYPES = new Class[] {
			 PowerSensor.class,
			 ElectricVehicle.class,
			 ElectricityStorage.class,
			 ElectricCurrentSensor.class,
			 ElectricVoltageSensor.class,
			 EnergyAccumulatedSensor.class,
			 SingleSwitchBox.class,
			 MultiSwitchBox.class,
			 CoolingDevice.class,
			 CombinedHeatAndPowerGenerator.class,
			 PVPlant.class,
			 HeatPump.class,
			 ElectricityMeter.class,
			 ElectricLight.class,
		};

	/**
	 * Apply standard tags to database content.
	 * @param rec
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public static boolean tagDatabase(final CloseableDataRecorder rec) {
		if (rec == null)
			return false;
		if (rec.isEmpty())
			return false;
		rec.getAllTimeSeries().forEach(ts -> {
			final Map<String, List<String>> map = TaggingUtils.getResourceTags(ts.getPath());
			ts.setProperties((Map) map);
		});
		return true;
	}

	/**
	 * Returns a map of standard tags for this resource, with keys defined by the
	 * constants in {@link LogDataTaggingConstants}.
	 * @param resource
	 * @return
	 */
	public static Map<String,List<String>> getResourceTags(final Resource resource) {
		final Map<String, List<String>> result = new HashMap<>();
//		Resource p = resource.getLocationResource();
		Resource p = resource;
		while (p != null) {
			checkForRoom(p, result);
			checkForDeviceTypeGeneric(p, result);
			checkForDeviceTypeSpecific(p, result);
			checkForApplicationDomain(p, result);
			checkForDataType(p, result);
			checkForBuilding(p, result);
			p = p.getParent();
		}
		return result;
	}

	/**
	 * Like {@link #getResourceTags(Resource)}, except that we cannot access the resource
	 * (e.g. because it is defined on another gateway), and we can only try to deduce the
	 * type from the path. Hence, this is less exact than {@link #getResourceTags(Resource)},
	 * and the latter method should be preferred, where possible.
	 * @param path
	 * @return
	 */
	public static Map<String, List<String>> getResourceTags(final String path0) {
		final Map<String, List<String>> result = new HashMap<>();
		final String path = path0.toLowerCase();
		Arrays.stream(SPECIFIC_DEVICE_TYPES)
			.filter(type -> path.contains(type.getSimpleName().toLowerCase()))
			.findFirst()
			.ifPresent(type -> addProperty(result, LogDataTaggingConstants.DEVICE_TYPE_SPECIFIC, type.getSimpleName()));
		final String resName;
		final int idx = path.lastIndexOf('/');
		resName = idx < 0 ? path : path.substring(idx+1);
		checkForDataType(resName, result);
		if (result.containsKey(LogDataTaggingConstants.DATA_TYPE)) {
			final List<String> list = result.get(LogDataTaggingConstants.DATA_TYPE);
			if (list.contains("measurement"))
				addProperty(result, LogDataTaggingConstants.DEVICE_TYPE_GENERIC, Sensor.class.getSimpleName());
			else if (list.contains("setpoint"))
				addProperty(result, LogDataTaggingConstants.DEVICE_TYPE_GENERIC, Actor.class.getSimpleName());
		}
		return result;
	}

	private final static void checkForDataType(final Resource p, final Map<String, List<String>> result) {
		if (result.containsKey(LogDataTaggingConstants.DATA_TYPE))
			return;
		final String name = p.getName().toLowerCase();
		switch (name) {
		case "reading":
			if (p.getParent() instanceof Sensor)
				addProperty(result, LogDataTaggingConstants.DATA_TYPE, "measurement");
			break;
		case "setpoint":
		case "statecontrol":
		case "statefeedback":
			if (p.getParent() instanceof Actor || p.getParent() instanceof TargetRange)
				addProperty(result, LogDataTaggingConstants.DATA_TYPE, "setpoint");
		default:
			return;
		}
	}

	private final static void checkForDataType(final String resNameLower, final Map<String, List<String>> result) {
		if (result.containsKey(LogDataTaggingConstants.DATA_TYPE))
			return;
		switch (resNameLower) {
		case "reading":
			result.put(LogDataTaggingConstants.DATA_TYPE, Collections.singletonList("measurement"));
			break;
		case "setpoint":
		case "statecontrol":
		case "statefeedback":
			result.put(LogDataTaggingConstants.DATA_TYPE, Collections.singletonList("setpoint"));
		default:
			return;
		}
	}

	private final static void checkForApplicationDomain(final Resource p, final Map<String, List<String>> result) {
		if (result.containsKey(LogDataTaggingConstants.APPLICATION_DOMAIN))
			return;
		if (!(p instanceof PhysicalElement) || p instanceof Room || p instanceof Building)
			return;
		Arrays.stream(HEATING_TYPES)
			.filter(type -> type.isAssignableFrom(p.getResourceType()))
			.findAny()
			.ifPresent(type -> 	addProperty(result, LogDataTaggingConstants.APPLICATION_DOMAIN, "Heating"));
		Arrays.stream(ELECTRICITY_TYPES)
			.filter(type -> type.isAssignableFrom(p.getResourceType()))
			.findAny()
			.ifPresent(type -> addProperty(result, LogDataTaggingConstants.APPLICATION_DOMAIN, "Electricity"));
		// TODO further types
	}

	private final static void checkForDeviceTypeSpecific(final Resource p, final Map<String, List<String>> result) {
		if (!(p instanceof PhysicalElement))
			return;
		appendSuperInterfaces(p.getResourceType(), result);
	}

	private final static void appendSuperInterfaces(final Class<?> itf, final Map<String, List<String>> result) {
		if (itf == PhysicalElement.class || itf == Sensor.class || itf == Actor.class || itf == Resource.class || !Resource.class.isAssignableFrom(itf))
			return;
		addProperty(result, LogDataTaggingConstants.DEVICE_TYPE_SPECIFIC, itf.getSimpleName());
		for (Class<?> superItf : itf.getInterfaces()) {
			appendSuperInterfaces(superItf, result);
		}
	}

	private final static void checkForDeviceTypeGeneric(final Resource p, final Map<String, List<String>> result) {
		if (result.containsKey(LogDataTaggingConstants.DEVICE_TYPE_GENERIC))
			return;
		if (p instanceof Sensor)
			addProperty(result, LogDataTaggingConstants.DEVICE_TYPE_GENERIC, Sensor.class.getSimpleName());
		else if (p instanceof Actor)
			addProperty(result, LogDataTaggingConstants.DEVICE_TYPE_GENERIC, Actor.class.getSimpleName());
	}

	private final static void checkForRoom(final Resource p, final Map<String, List<String>> result) {
		Room room = null;
		if (p instanceof Room) {
			room = (Room) p;
		}
		else if (p instanceof PhysicalElement && (!(p instanceof Building))) {
			if (((PhysicalElement) p).location().room().isActive()) {
				room = ((PhysicalElement) p).location().room();
			}
		}
		if (room != null) {
			addProperty(result, LogDataTaggingConstants.ROOM_PATH, room.getLocation());
			if (room.name().isActive())
				addProperty(result, LogDataTaggingConstants.ROOM_NAME, room.name().getValue());
			if (room.type().isActive())
				addProperty(result, LogDataTaggingConstants.ROOM_TYPE, room.type().getValue() + "");
		}
	}

	private final static void checkForBuilding(final Resource p, final Map<String, List<String>> result) {
		if (result.containsKey(LogDataTaggingConstants.BUILDING_PATH))
			return;
		Building building = null;
		if (p instanceof Building)
			building = (Building) p;
		else if (p instanceof PhysicalElement) {
			if (((PhysicalElement) p).location().device() instanceof Building)
				building = (Building) ((PhysicalElement) p).location().device();
		}
		if (building != null) {
			addProperty(result, LogDataTaggingConstants.BUILDING_PATH, building.getLocation());
			if (building.name().isActive())
				addProperty(result, LogDataTaggingConstants.BUILDING_NAME, building.name().getValue());
		}
	}

	private static void addProperty(final Map<String,List<String>> result, final String key, final String value) {
		final List<String> list0 = result.get(key);
		if (list0 == null) {
			final List<String> list = new ArrayList<>(2);
			list.add(value);
			result.put(key, list);
		} else {
			if (!list0.contains(value))
				list0.add(value);
		}
	}

}
