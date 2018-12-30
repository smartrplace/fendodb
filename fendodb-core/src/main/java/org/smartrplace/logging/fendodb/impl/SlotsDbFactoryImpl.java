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
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import org.ogema.core.administration.FrameworkClock;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.osgi.service.component.annotations.ReferencePolicyOption;
import org.slf4j.LoggerFactory;
import org.smartrplace.logging.fendodb.CloseableDataRecorder;
import org.smartrplace.logging.fendodb.DataRecorderReference;
import org.smartrplace.logging.fendodb.FendoDbConfiguration;
import org.smartrplace.logging.fendodb.FendoDbFactory;
import org.smartrplace.logging.fendodb.accesscontrol.FendoDbAccessControl;
import org.smartrplace.logging.fendodb.permissions.FendoDbPermission;

@Component(immediate=true, service=FendoDbFactory.class)
public class SlotsDbFactoryImpl implements FendoDbFactory {

	private final CompletableFuture<FendoDbFactory> selfFuture = new CompletableFuture<>();
	// Map<Path relative to rundir, database>
	// synchronized on itself
	private final Map<Path,SlotsDb> instances = new ConcurrentHashMap<>(4);
	// also synchronized on instances
	private final Map<Path, SlotsDb> pendingInstances = new HashMap<>(4);
	// closed but existent instances; modifications synchronized on instances
	private final List<Path> closedInstances = new CopyOnWriteArrayList<>();
	private final static Path BASE = Paths.get(".");
	private final List<SlotsDbListener> listeners = new CopyOnWriteArrayList<>();
	private volatile ServiceRegistration<?> shellCommands;
	private volatile ExecutorService exec;
	private final Queue<Future<?>> pendingCallbacks = new ConcurrentLinkedQueue<>();
	private final Semaphore cleanUpSema = new Semaphore(1);
	volatile boolean isSecure = System.getSecurityManager() != null;
	private final AtomicBoolean closed = new AtomicBoolean(true);
	private static final String PERSISTENCE_FILE = "slotsdbs.ser";
	private volatile Path persistence;
	private volatile Runnable persistenceTask;

	@Reference(cardinality=ReferenceCardinality.OPTIONAL)
	private FrameworkClock clock;
	
	@Reference(
			service=FendoDbAccessControl.class,
			cardinality=ReferenceCardinality.OPTIONAL,
			policy=ReferencePolicy.DYNAMIC,
			policyOption=ReferencePolicyOption.GREEDY
	)
	volatile FendoDbAccessControl accessManager;

	// ctx is null in tests... must be able to deal with this case
	@SuppressWarnings("unchecked")
	@Activate
	public void activate(final BundleContext ctx) {
		isSecure = System.getSecurityManager() != null;
		final Hashtable<String, Object> props = new Hashtable<String, Object>();
		props.put("osgi.command.scope", "fendodb");
		props.put("osgi.command.function", new String[] {
			"addProperty",
			"closeFendoDb",
			"copyFendoDb",
			"deleteDataAfter",
			"deleteDataBefore",
			"deleteDataOlderThan",
			"findTimeSeries",
			"getAllFendoDbTimeSeries",
			"getAllProperties",
			"getAllPropertyValues",
			"getPropertiesById",
			"getReferenceCount",
			"getFendoDbs",
			"getFendoDbConfig",
			"getFendoDbTimeSeries",
			"isFendoDbActive",
			"openFendoDb",
			"removeProperty",
			"setProperty",
			"fendoDbExists",
			"reloadDays",
			"updateConfig"
		});
		this.exec = Executors.newSingleThreadExecutor();
		try {
			this.shellCommands = ctx.registerService(org.smartrplace.logging.fendodb.impl.GogoCommands.class,
					new GogoCommands(this), props);
			// gogo shell is an optional dependency // null in tests or if used without OSGi
		} catch (NoClassDefFoundError | NullPointerException expected) {
		}
		closed.set(false);
		this.persistence = ctx != null ? ctx.getDataFile(PERSISTENCE_FILE).toPath() : null;
		if (persistence != null && Files.isRegularFile(persistence)) {
			try (ObjectInputStream in = new ObjectInputStream(Files.newInputStream(persistence))) {
				final Set<String> knownInstances = (Set<String>) in.readObject();
				if (knownInstances != null) {
					knownInstances.stream()
						.map(path -> Paths.get(path))
						.filter(path -> Files.isDirectory(path))
						.filter(path -> Files.isRegularFile(path.resolve(SlotsDb.STORAGE_PERSISTENCE_FILE)))
						.forEach(path -> closedInstances.add(path));
				}
			} catch (IOException | ClassNotFoundException e) {
				LoggerFactory.getLogger(SlotsDbFactoryImpl.class).error("Failed to load persisted database infos",e);
			}
		}
		persistenceTask = persistence == null ? null : new Runnable() {

			private final Semaphore sema = new Semaphore(1);

			@Override
			public void run() {
				final Path path = persistence;
				if (closed.get() || path == null)
					return;
				if (!sema.tryAcquire())
					return;
				try {
					final Set<String> knownInstances = new HashSet<>();
					synchronized (instances) {
						instances.keySet().forEach(p -> knownInstances.add(p.toString()));
						pendingInstances.keySet().forEach(p -> knownInstances.add(p.toString()));
						closedInstances.forEach(p -> knownInstances.add(p.toString()));
					}
					try (ObjectOutputStream oos = new ObjectOutputStream(Files.newOutputStream(path))) {
						oos.writeObject(knownInstances);
					} catch (IOException e) {
						LoggerFactory.getLogger(SlotsDbFactoryImpl.class).error("Failed to persist database infos",e);
					}
				} finally {
					sema.release();
				}
			}
		};
		this.selfFuture.complete(this);
	}

	@Deactivate
	public void deactivate() {
		closed.set(true);
		final ServiceRegistration<?> sreg  = shellCommands;
		this.shellCommands = null;
		final ExecutorService exec = this.exec;
		if (sreg != null) {
			try {
				sreg.unregister();
			} catch (Exception irrelevant) {}
		}
		synchronized (instances) {
			final Collection<SlotsDb> copy = new ArrayList<>(instances.values());
			copy.forEach(slots -> slots.close()); // removes instance from instances map and triggers listeners
		}
		this.exec = null;
		if (exec != null) {
			exec.shutdown();
			if (!exec.isTerminated()) {
				try {
					exec.awaitTermination(2, TimeUnit.SECONDS);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
				if (!exec.isTerminated())
					exec.shutdownNow();
			}
		}
		this.selfFuture.cancel(true);
		this.persistence = null;
		this.persistenceTask = null;
	}

	private final void triggerPersistence() {
		if (persistenceTask != null)
			new Thread(persistenceTask, "SlotsDbFactory-persistence").start();
	}


	// needs external synchro on callbackSema
	private final ExecutorService getExecutor() {
		if (!pendingCallbacks.isEmpty() && cleanUpSema.tryAcquire()) {
			try {
				new Thread(cleanUpTask, "SlotsDbFactory-clean up").start();
			} catch (Throwable e) {
				cleanUpSema.release();
				throw e;
			}
		}
		return this.exec;
	}

	private final Runnable cleanUpTask = new Runnable() {

		@Override
		public void run() {
			try {
				cleanUp();
			} finally {
				cleanUpSema.release();
			}
		}

		private void cleanUp() {
			final Iterator<Future<?>> it = pendingCallbacks.iterator();
			while (it.hasNext()) {
				final Future<?> f = it.next();
				if (!f.isDone()) {
					try {
						f.get(5, TimeUnit.SECONDS);
					} catch (InterruptedException e) {
						Thread.currentThread().interrupt();
						return;
					} catch (ExecutionException dontcare) {
					} catch (TimeoutException | CancellationException e) {
						 // TODO what if the task still does not stop? Do we need to create a new thread, or could we continue with the old one?
						f.cancel(true);
//						final ExecutorService exec = this.exec;
//						if (exec != null) {
//							exec.shutdownNow();
//							this.exec = Executors.newSingleThreadExecutor();
//						}
					}
				}
				it.remove();
			}
		}

	};

	@Override
	public CloseableDataRecorder getInstance(Path baseFolder) throws IOException {
		return getInstance(baseFolder, null);
	};

	@Override
	public CloseableDataRecorder getInstance(Path baseFolder, FendoDbConfiguration configuration) throws IOException {
		Objects.requireNonNull(baseFolder);
		if (closed.get())
			throw new IllegalStateException("SlotsFactory has been closed");
		baseFolder = normalize(BASE.resolve(baseFolder));
		SlotsDb db = null;
		boolean exists = false;
		synchronized (instances) {
			db = getSlotsDb(baseFolder);
			exists = db != null;
			if (!exists) {
				if (getPendingSlotsDb(baseFolder) != null)
					return null;
				boolean configUpdate = configuration != null && !configuration.isReadOnlyMode();
				if (isSecure) {
					PermissionUtils.checkPermission(baseFolder, FendoDbPermission.READ, accessManager);
					configUpdate = configUpdate && PermissionUtils.mayWrite(baseFolder, accessManager);
				}
				db = new SlotsDb(baseFolder, clock, configuration, this, configUpdate);
				instances.put(baseFolder, db); // would be added in listener callback too, but better add it here already, to avoid race conditions
			}
			try {
				final boolean readOnly = (configuration != null && configuration.isReadOnlyMode())
						|| (configuration == null && isSecure && !PermissionUtils.mayWrite(baseFolder, accessManager));
				if (isSecure && !readOnly)
					PermissionUtils.checkWritePermission(baseFolder, accessManager);
				// otherwise admin permission has been checked already
				if (exists && isSecure) {
					PermissionUtils.checkReadPermission(baseFolder, accessManager);
				}
				closedInstances.remove(baseFolder);
				if (!exists)
					triggerListeners(db, true);
				return db.getProxyDb(readOnly);
			} catch (Throwable e) {
				if (!exists) {
					try {
						db.close();
					} catch (Exception ignore) {}
					instances.remove(baseFolder);
				}
				throw e;
			}
		}
	}

	// synchronize externally on instances
	private SlotsDb getSlotsDb(Path path) {
		for (Map.Entry<Path, SlotsDb> entry: instances.entrySet()) {
			try {
				if (Files.isSameFile(entry.getKey(), path))
					return entry.getValue();
			} catch (IOException e) {
				// ?
				if (entry.getKey().equals(path))
					return entry.getValue();
			}
		}
		return null;
	}

	// synchronize externally on instances //
	private SlotsDb getPendingSlotsDb(Path path) {
		for (Map.Entry<Path, SlotsDb> entry: pendingInstances.entrySet()) {
			try {
				if (Files.isSameFile(entry.getKey(), path))
					return entry.getValue();
			} catch (IOException e) {
				// ?
				if (entry.getKey().equals(path))
					return entry.getValue();
			}
		}
		return null;
	}

	// called by the db itself
	void remove(final SlotsDb slotsdb, final boolean updatePending, final boolean fromFinalizer) {
		boolean found = false;
		synchronized (instances) {
			Iterator<SlotsDb> it = instances.values().iterator();
			while (it.hasNext()) {
				SlotsDb db = it.next();
				if (db.equals(slotsdb)) {
					it.remove();
					found = true;
					break;
				}
			}
			if (found && updatePending)
				pendingInstances.put(slotsdb.getPath(), slotsdb);
			else if (found)
				closedInstances.add(slotsdb.getPath());
		}
		// if the call comes from the finalizer of the SlotsDb, we must not recreate any references to it
		if (!fromFinalizer && found && !listeners.isEmpty()) {
			final ExecutorService exec = getExecutor();
			listeners.forEach(l -> pendingCallbacks.add(exec.submit(new ListenerRunnable(l, slotsdb, isSecure, false))));
		}
	}

	private boolean removePending(final SlotsDb slotsdb) {
		boolean found = false;
		synchronized (instances) {
			Iterator<SlotsDb> it = pendingInstances.values().iterator();
			while (it.hasNext()) {
				SlotsDb db = it.next();
				if (db.equals(slotsdb)) {
					it.remove();
					found = true;
					break;
				}
			}
		}
		return found;
	}

	@Override
	public Map<Path, DataRecorderReference> getAllInstances() {
		if (instances.isEmpty() && closedInstances.isEmpty())
			return Collections.emptyMap();
		else {
			final Map<Path, DataRecorderReference> map = new HashMap<>(instances.size());
			for (Map.Entry<Path, SlotsDb> entry : instances.entrySet()) {
				if (!isSecure || PermissionUtils.mayRead(entry.getKey(), accessManager)) // write permission will be determined by proxy
					map.put(entry.getKey(), new FendoDbReference(entry.getValue(), isSecure));
			}
			if (!closedInstances.isEmpty()) {
				synchronized (instances) {
					for (Path closed: closedInstances) {
						if (isSecure && !PermissionUtils.mayRead(closed, accessManager))
							continue;
						try {
							final SlotsDb slotsDb = new SlotsDb(closed, clock, null, this);
							instances.put(closed, slotsDb);
							map.put(closed, new FendoDbReference(slotsDb, isSecure));
							closedInstances.remove(closed);
						} catch (IOException ignore) {
						}
					}
				}
			}
			return map;
		}
	}

	SlotsDb getExistingInstanceInternal(Path baseFolder) throws IOException {
		Objects.requireNonNull(baseFolder);
		if (closed.get())
			throw new IllegalStateException("SlotsFactory has been closed");
		if (isSecure)
			PermissionUtils.checkReadPermission(baseFolder, accessManager);
		baseFolder = normalize(BASE.resolve(baseFolder));
		SlotsDb db = getSlotsDb(baseFolder);
		if (db != null)
			return db;
		if (closedInstances.contains(baseFolder) ||
				Files.isRegularFile(baseFolder.resolve(SlotsDb.STORAGE_PERSISTENCE_FILE))) {
			final CloseableDataRecorder rec = getInstance(baseFolder);
			if (rec == null)
				return null;
			return ((SlotsDbProxy) rec).master;
		}
		return null;
	}

	@Override
	public CloseableDataRecorder getExistingInstance(Path baseFolder) throws IOException {
		Objects.requireNonNull(baseFolder);
		if (closed.get())
			throw new IllegalStateException("SlotsFactory has been closed");
		if (isSecure)
			PermissionUtils.checkReadPermission(baseFolder, accessManager);
		baseFolder = normalize(BASE.resolve(baseFolder));
		SlotsDb db = getSlotsDb(baseFolder);
		if (db != null)
			return db.getProxyDb();
		if (closedInstances.contains(baseFolder) ||
				Files.isRegularFile(baseFolder.resolve(SlotsDb.STORAGE_PERSISTENCE_FILE)))
			return getInstance(baseFolder);
		return null;
	}

	@Override
	public boolean databaseExists(Path path) {
		path = path.normalize();
		synchronized(instances) {
			if (instances.containsKey(path))
				return true;
			if (pendingInstances.containsKey(path))
				return true;
			if (Files.isRegularFile(path.resolve(SlotsDb.STORAGE_PERSISTENCE_FILE))
					|| Files.isRegularFile(path.resolve(SlotsDb.CONFIG_PERSISTENCE_FILE)))
				return true;
		}
		return false;
	}

	boolean databaseIsActive(Path path) {
		path = path.normalize();
		synchronized(instances) {
			if (instances.containsKey(path))
				return instances.get(path).isActive();
		}
		return false;
	}

	@Override
	public void addDatabaseListener(final SlotsDbListener listener) {
		if (listener == this)
			return;
		if (listeners.contains(listener))
			return;
		listeners.add(listener);
		final Map<Path, SlotsDb> instances = new HashMap<>(this.instances);
		if (!instances.isEmpty()) {
			final ExecutorService exec = getExecutor();
			instances.values().forEach(db -> pendingCallbacks.add(exec.submit(new ListenerRunnable(listener, db, isSecure, true))));
		}
	}

	@Override
	public void removeDatabaseListener(SlotsDbListener listener) {
		listeners.remove(listener);
	}

	final void triggerListener(final SlotsDb instance, final SlotsDbListener listener, final boolean availableOrRemoved) {
		pendingCallbacks.add(getExecutor().submit(new ListenerRunnable(listener, instance, isSecure, availableOrRemoved)));
	}

	final void triggerListeners(final SlotsDb instance, final boolean availableOrRemoved) {
		if (!listeners.isEmpty()) {
			final ExecutorService exec = getExecutor();
			listeners.forEach(l -> pendingCallbacks.add(exec.submit(new ListenerRunnable(l, instance, isSecure, availableOrRemoved))));
		}
		if (availableOrRemoved) {
			triggerPersistence();
		}
	}

	private static final class ListenerRunnable implements Runnable {

		private final SlotsDbListener listener;
		private final FendoDbReference db;
		private final boolean addedOrRemoved;

		ListenerRunnable(SlotsDbListener listener, CloseableDataRecorder db, boolean secure, boolean addedOrRemoved) {
			this.listener = listener;
			this.db = new FendoDbReference((SlotsDb) db, secure);
			this.addedOrRemoved = addedOrRemoved;
		}

		@Override
		public void run() {
			if (addedOrRemoved)
				listener.databaseStarted(db);
			else
				listener.databaseClosed(db);
		}

	}

	final SlotsDbListener ownListener = new SlotsDbListener() {

		@Override
		public void databaseStarted(DataRecorderReference ref) {
			final SlotsDb db = ((FendoDbReference) ref).getMaster();
			final CloseableDataRecorder old;
			synchronized (instances) {
				old = instances.get(db.getPath());
				if (old != db)
					instances.put(db.getPath(), db);
				removePending(db);
			}
			final boolean isNew = old == null;
			if (isNew)
				triggerListeners(db, true);
		}

		@Override
		public void databaseClosed(DataRecorderReference ref) {
			final SlotsDb db = ((FendoDbReference) ref).getMaster();
			final boolean removed;
			synchronized (instances) {
				removed = instances.values().remove(db);
			}
			if (removed) {
				triggerListeners(db, false);
				closedInstances.add(db.getPath());
			}
		}
	};
	
	static Path normalize(final Path path) {
		return path.normalize();
	}
	
	@Reference(
			service=FendoInitImpl.class,
			cardinality=ReferenceCardinality.MULTIPLE,
			policy=ReferencePolicy.DYNAMIC,
			policyOption=ReferencePolicyOption.GREEDY,
			bind="addInitConfig",
			unbind="removeInitConfig"
	)
	protected void addInitConfig(FendoInitImpl init) throws IOException {
		init.init(selfFuture);
	}
	
	protected void removeInitConfig(FendoInitImpl init) {} // nothing to do
	
}
