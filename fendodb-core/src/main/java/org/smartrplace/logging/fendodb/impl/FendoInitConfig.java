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
package org.smartrplace.logging.fendodb.impl;

import org.osgi.service.metatype.annotations.ObjectClassDefinition;

/**
 * Configuration to load FendoDb instances at framework start.
 */
@ObjectClassDefinition
public @interface FendoInitConfig {
	
	String path();
	boolean readOnly() default false;
	boolean useCompatibilityMode() default false;
	boolean parseFoldersOnInit() default false;
	/**
	 * Database flush period in ms.
	 * @return
	 */
	long flushPeriod() default 10000;
	/**
	 * Max data lifetime in days. Set to 0 to keep data forever.
	 * @return
	 */
	int dataLifeTimeDays() default 0;
	/**
	 * Size limit in bytes. Older files will be deleted when
	 * the limit is exceeded. Set to 0 to disable size limit.
	 * @return
	 */
	int dataLimitSize() default 0;
	
	/**
	 * Default: 1 day.
	 * @return
	 */
	long dataExpirationCheckInterval() default 24 * 60 * 60 * 1000; 
	
	int maxOpenFolders() default 512;

}
