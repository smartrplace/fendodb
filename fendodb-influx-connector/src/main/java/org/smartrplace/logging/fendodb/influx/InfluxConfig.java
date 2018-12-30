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
package org.smartrplace.logging.fendodb.influx;

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.AttributeType;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

/**
 * Also requires constants
 * <ul>
 * 	 <li>org.smartrplace.tools.housekeeping.Period</li>
 *   <li>org.smartrplace.tools.housekeeping.Delay</li>
 *   <li>org.smartrplace.tools.housekeeping.Unit</li>
 * </ul>
 */
@ObjectClassDefinition
public @interface InfluxConfig {
	
	public static final String PID = "org.smartrplace.logging.fendodb.InfluxExport";

	String url();
	String user();
	@AttributeDefinition(type=AttributeType.PASSWORD)
	String pw();
	String influxdb();
	/**
	 * Wildcard "*" allowed -> transfer all instances
	 * @return
	 */
	String fendodb();
	String measurementid();
	
	@AttributeDefinition(description = 
			"Either a long value (millis since 1st Jan 1970) or a String matching \"yyyy-MM-dd'T'HH:mm:ssZ\", "
			+ "where all parts beyond the year are optional")
	String startTime() default "";
	@AttributeDefinition(description = 
			"Either a long value (millis since 1st Jan 1970) or a String matching \"yyyy-MM-dd'T'HH:mm:ssZ\", "
			+ "where all parts beyond the year are optional")
	String endTime() default "";
	
}
