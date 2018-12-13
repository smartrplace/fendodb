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

import org.ogema.core.model.Resource;
import org.ogema.model.locations.Room;

public class LogDataTaggingConstants {

	/**
	 * Typical values for this key:
	 * <ul>
	 * 	<li>Sensor
	 *  <li>Actor
	 * </ul>
	 */
	public static final String DEVICE_TYPE_GENERIC = "deviceTypeGeneric";
	
	/**
	 * Typical values for this key
	 * <ul>
	 * 	<li>Thermostat
	 *  <li>TemperatureSensor
	 *  <li>PVPlant
	 *  <li>
	 * </ul>
	 * The value corresponds to the simple name of the main device's resource type,
	 * see OGEMA models.
	 */
	public static final String DEVICE_TYPE_SPECIFIC = "deviceTypeSpecific";

	public static final String DEVICE_PATH = "devicePath";
	
	public static final String DEVICE_NAME = "deviceName";
	
	/**
	 * Typical values for this key:
	 * <ul>
	 * 	<li>Heating
	 *  <li>Electricity
	 *  <li>Security
	 *  <li>...
	 * </ul>
	 */
	public static final String APPLICATION_DOMAIN = "applicationDomain";
	
	/**
	 * The room the data belongs to / the device is located in.
	 * The value should be equal to the resource location of the room, see
	 * {@link Resource#getLocation()}.
	 */
	public static final String ROOM_PATH = "roomPath";
	
	public static final String ROOM_NAME = "roomName";

	/**
	 * The room type according to {@link Room#type()}.
	 * The values are integers.
	 */
	public static final String ROOM_TYPE = "roomType";
	
	/**
	 * The building this data belongs to.
	 */
	public static final String BUILDING_PATH = "buildingPath";
	
	/**
	 * The building this data belongs to.
	 */
	public static final String BUILDING_NAME = "buildingName";
	
	/**
	 * Typical values for this key are
	 * <ul>
	 * 	<li>measurement: for data that represents a measured value, such as a temperature reading
	 *  <li>setpoint: for data that represent a setpoint of an actor, such as a thermostat valve setpoint
	 * </ul>
	 */
	public static final String DATA_TYPE = "dataType";
	
	/**
	 * The gateway id
	 */
	public static final String GATEWAY_ID = "gatewayId";
	
	/**
	 * Specific for electricity: phase information. Values:
	 * <ul>
	 * 	 <li>total
	 *   <li>phase1
	 *   <li>phase2
	 *   <li>phase3
	 *   <li>...
	 * </ul>
	 */
	public static final String PHASE = "phase";
	
}
