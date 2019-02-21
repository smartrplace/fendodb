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

import java.io.IOException;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.osgi.service.component.ComponentException;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.metatype.annotations.Designate;
import org.slf4j.LoggerFactory;
import org.smartrplace.logging.fendodb.CloseableDataRecorder;
import org.smartrplace.logging.fendodb.FendoDbConfiguration;
import org.smartrplace.logging.fendodb.FendoDbConfigurationBuilder;
import org.smartrplace.logging.fendodb.FendoDbFactory;

@Component(
		service = FendoInitImpl.class,
		configurationPid = FendoInitImpl.PID,
		configurationPolicy = ConfigurationPolicy.REQUIRE,
		property = {
				"service.factoryPid=" + FendoInitImpl.PID
		}
)
@Designate(factory=true, ocd=FendoInitConfig.class)
public class FendoInitImpl {
	
	static final String PID = "org.smartrplace.logging.FendoDbInit";
	
	volatile FendoInitConfig config;
	private volatile CloseableDataRecorder instance;
	private volatile CompletableFuture<Void> thisInit; 
	
	@Activate
	protected void activate(FendoInitConfig config) throws IOException {
		this.config = config;
		try {
			Paths.get(config.path());
		} catch (InvalidPathException e) {
			throw new ComponentException("Invalid FendoDb init configuration",e);
		}
	}
	
	void init(final CompletableFuture<FendoDbFactory> factoryFuture)  {
		thisInit = factoryFuture.thenAccept(factory -> {
			final String pathStr = config.path();
			final Path path;
			try {
				path = Paths.get(pathStr);
			} catch (InvalidPathException e) {
				LoggerFactory.getLogger(getClass()).error("Invalid path {}", config.path(), e);
				return;
			}
			try {
				final FendoDbConfiguration cfg = FendoDbConfigurationBuilder.getInstance()
						.setUseCompatibilityMode(config.useCompatibilityMode())
						.setMaxOpenFolders(config.maxOpenFolders())
						.setFlushPeriod(config.flushPeriod())
						.setDataLifetimeInDays(config.dataLifeTimeDays())
						.setDataExpirationCheckInterval(config.dataExpirationCheckInterval())
						.setReadOnlyMode(config.readOnly())
						.setMaxDatabaseSize(config.dataLimitSize())
						.setParseFoldersOnInit(config.parseFoldersOnInit())
						.build();
				if (!config.constructEagerly()) {
					((SlotsDbFactoryImpl) factory).addClosedInstance(path, cfg);
					return;
				}
				CloseableDataRecorder instance = factory.getExistingInstance(path);
				if (instance == null) {
					instance = factory.getInstance(path, cfg);
				}
				this.instance = instance;
			} catch (Exception e) {
				LoggerFactory.getLogger(getClass()).warn("Could not create FendoDb instance {}", path, e);
			}
		});
		
	}
	
	@Deactivate
	protected void deactivate() {
		final CompletableFuture<Void> thisInit = this.thisInit;
		if (thisInit != null) {
			thisInit.cancel(false);
			if (!thisInit.isDone()) {
				try {
					thisInit.get(2, TimeUnit.SECONDS);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				} catch (TimeoutException ignore) {
				} catch (Exception e) {
					LoggerFactory.getLogger(getClass()).warn("Failed to create FendoDb instance {}", e);
				}
			}
		}
		final CloseableDataRecorder instance = this.instance;
		if (instance != null) {
			try {
				instance.close();
			} catch (Exception e) {
				LoggerFactory.getLogger(getClass()).warn("Could not close FendoDb instance",e);
			}
		}
	}
	
}
