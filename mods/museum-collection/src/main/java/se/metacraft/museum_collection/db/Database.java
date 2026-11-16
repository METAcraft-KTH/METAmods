package se.metacraft.museum_collection.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import se.metacraft.museum_collection.MuseumCollection;
import se.metacraft.museum_collection.MuseumConfig;

import java.sql.SQLException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The connection pool and the threads every query runs on, so the server thread never waits for the database.
 * Nothing here connects when it is made: the pool opens its connections on those threads, and a database that
 * is down (at startup or later) fails the queries asked of it until it is back, which is when the tables are
 * created if they still have to be.
 */
public final class Database {

	@FunctionalInterface
	public interface Query<T> {
		T run(EventRepository repository) throws SQLException;
	}

	private final MuseumConfig.Database config;
	private final HikariDataSource dataSource;
	private final ExecutorService executor;
	private final EventRepository repository;
	private boolean tablesCreated;

	public Database(MuseumConfig.Database config) {
		this.config = config;
		var hikari = new HikariConfig();
		hikari.setPoolName(MuseumCollection.MODID);
		// Named, so the driver bundled in this mod's jar is found whatever DriverManager has registered.
		hikari.setDriverClassName("org.mariadb.jdbc.Driver");
		hikari.setJdbcUrl(config.jdbcUrl());
		hikari.setUsername(config.username());
		hikari.setPassword(config.resolvedPassword());
		hikari.setMaximumPoolSize(config.poolSize());
		hikari.setMinimumIdle(1);
		hikari.setConnectionTimeout(TimeUnit.SECONDS.toMillis(config.connectTimeoutSeconds()));
		// Don't connect (or fail) in the constructor: that would be the server thread.
		hikari.setInitializationFailTimeout(-1);
		this.dataSource = new HikariDataSource(hikari);
		this.repository = new EventRepository(dataSource::getConnection, config.tablePrefix());
		var threadNumber = new AtomicInteger();
		this.executor = Executors.newFixedThreadPool(config.poolSize(), task -> {
			var thread = new Thread(task, MuseumCollection.MODID + "-db-" + threadNumber.incrementAndGet());
			thread.setDaemon(true);
			return thread;
		});
	}

	public MuseumConfig.Database config() {
		return config;
	}

	/**
	 * Runs a query on a database thread. The future completes on that thread too: hand its result back with
	 * {@code server.execute} before touching the game.
	 */
	public <T> CompletableFuture<T> submit(Query<T> query) {
		var future = new CompletableFuture<T>();
		try {
			executor.execute(() -> {
				try {
					createTablesOnce();
					future.complete(query.run(repository));
				} catch (Throwable e) {
					future.completeExceptionally(e);
				}
			});
		} catch (RejectedExecutionException e) {
			future.completeExceptionally(e);
		}
		return future;
	}

	private synchronized void createTablesOnce() throws SQLException {
		if (!tablesCreated) {
			repository.createTables();
			tablesCreated = true;
			MuseumCollection.LOGGER.info("Event database ready: {}", config);
		}
	}

	/**
	 * Closes the pool once the queries already asked for have run.
	 *
	 * @param wait whether to block (a few seconds at most) until they have: for a server that is stopping
	 */
	public void close(boolean wait) {
		// The pool closes only once every query thread is done: closing it from one of them would fail the
		// queries the other threads are still running or have yet to take.
		executor.shutdown();
		if (wait) {
			closeWhenDone();
		} else {
			var closer = new Thread(this::closeWhenDone, MuseumCollection.MODID + "-db-close");
			closer.setDaemon(true);
			closer.start();
		}
	}

	private void closeWhenDone() {
		try {
			if (!executor.awaitTermination(config.connectTimeoutSeconds() + 2L, TimeUnit.SECONDS)) {
				MuseumCollection.LOGGER.warn("Event database queries were still running when the database was closed");
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		} finally {
			dataSource.close();
		}
	}
}
