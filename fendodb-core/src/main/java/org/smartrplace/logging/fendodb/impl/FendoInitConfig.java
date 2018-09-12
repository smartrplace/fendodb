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
