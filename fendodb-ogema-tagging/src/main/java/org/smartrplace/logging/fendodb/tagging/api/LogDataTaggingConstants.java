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
	 * Relevant if {@link #DATA_TYPE} is "setpoint". Typical values:
	 * <ul>
	 * 	<li>managementSetting: for a setpoint that is being set on the gateway, either by an app 
	 * 		or via user interaction
	 *  <li>deviceFeedback: for a setpoint that is reported back by an external device to the gateway
	 * </ul>
	 */
	public static final String SETPOINT_TYPE = "setpointType";
	
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
	
	/**
	 * Typical values:
	 * <ul>
	 * 	 <li>gas
	 * 	 <li>water
	 *   <li>coal
	 *   <li>...
	 * </ul>
	 */
	public static final String COMMODITY = "commodity";
	
}
