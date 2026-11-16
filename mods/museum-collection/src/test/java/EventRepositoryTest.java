import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import se.metacraft.museum_collection.Team;
import se.metacraft.museum_collection.db.EventRepository;
import se.metacraft.museum_collection.db.EventRepository.Outcome;
import se.metacraft.museum_collection.db.EventRepository.PlayerInfo;

import java.sql.Connection;
import java.sql.Driver;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The event's SQL and the rules its schema enforces, against an in-memory H2 in MySQL mode. */
public class EventRepositoryTest {

	private static final Driver H2 = loadH2();
	private static final AtomicInteger DATABASES = new AtomicInteger();
	private static final UUID ALVA = UUID.fromString("00000000-0000-0000-0000-00000000000a");
	private static final UUID BO = UUID.fromString("00000000-0000-0000-0000-00000000000b");
	private static final UUID CLEO = UUID.fromString("00000000-0000-0000-0000-00000000000c");

	private String url;
	private EventRepository repository;

	@BeforeEach
	void createTables() throws SQLException {
		url = "jdbc:h2:mem:museum" + DATABASES.incrementAndGet() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
		repository = new EventRepository(this::connect, "museum_");
		repository.createTables();
	}

	/** The driver itself, not DriverManager: that looks drivers up by the calling thread's class loader, which a pool thread here doesn't share. */
	private Connection connect() throws SQLException {
		return H2.connect(url, new Properties());
	}

	private static Driver loadH2() {
		try {
			return (Driver) Class.forName("org.h2.Driver").getDeclaredConstructor().newInstance();
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException("H2 is not on the test classpath", e);
		}
	}

	@Test
	public void creatingTheTablesTwiceIsHarmless() throws SQLException {
		repository.setTeam(ALVA, "Alva", null, Team.EAST);
		repository.submit(ALVA, "Alva", null, "minecraft:dirt");
		repository.createTables();
		assertEquals(Optional.of(new PlayerInfo(Team.EAST, 1)), repository.loadPlayer(ALVA, "Alva", null));
	}

	@Test
	public void aPlayerOnNoTeamCannotSubmit() throws SQLException {
		assertEquals(Optional.empty(), repository.loadPlayer(ALVA, "Alva", null));
		var result = repository.submit(ALVA, "Alva", null, "minecraft:dirt");
		assertEquals(Outcome.NO_TEAM, result.outcome());
		assertNull(result.team());
		assertEquals(0, repository.standings(10).top().size());
	}

	@Test
	public void aPlayerSubmitsAnItemOnce() throws SQLException {
		repository.setTeam(ALVA, "Alva", null, Team.EAST);
		var first = repository.submit(ALVA, "Alva", null, "minecraft:diamond_sword");
		assertEquals(Outcome.REGISTERED, first.outcome());
		assertEquals(Team.EAST, first.team());
		assertTrue(first.firstForTeam());
		assertEquals(1, first.playerItems());
		assertEquals(1, first.teamItems());

		var again = repository.submit(ALVA, "Alva", null, "minecraft:diamond_sword");
		assertEquals(Outcome.ALREADY_SUBMITTED, again.outcome());
		assertFalse(again.firstForTeam());
		assertEquals(1, again.playerItems());
		assertEquals(1, again.teamItems());
	}

	@Test
	public void anyPlayerMaySubmitAnItemSomeoneElseHas() throws SQLException {
		repository.setTeam(ALVA, "Alva", null, Team.EAST);
		repository.setTeam(BO, "Bo", null, Team.EAST);
		repository.setTeam(CLEO, "Cleo", null, Team.WEST);
		repository.submit(ALVA, "Alva", null, "minecraft:dirt");

		var teammate = repository.submit(BO, "Bo", null, "minecraft:dirt");
		assertEquals(Outcome.REGISTERED, teammate.outcome());
		assertFalse(teammate.firstForTeam(), "the team already had it");
		assertEquals(1, teammate.playerItems());
		assertEquals(1, teammate.teamItems(), "a team's score counts an item once");

		var rival = repository.submit(CLEO, "Cleo", null, "minecraft:dirt");
		assertEquals(Outcome.REGISTERED, rival.outcome());
		assertTrue(rival.firstForTeam(), "new for the other team");
		assertEquals(1, rival.teamItems());
	}

	@Test
	public void standingsCountDistinctItemsPerTeamAndRankPlayers() throws SQLException {
		repository.setTeam(ALVA, "Alva", null, Team.EAST);
		repository.setTeam(BO, "Bo", null, Team.EAST);
		repository.setTeam(CLEO, "Cleo", null, Team.SOUTH);
		for (String item : List.of("minecraft:dirt", "minecraft:stone", "minecraft:sand")) {
			repository.submit(ALVA, "Alva", null, item);
		}
		repository.submit(BO, "Bo", null, "minecraft:dirt");
		repository.submit(BO, "Bo", null, "minecraft:gravel");
		repository.submit(CLEO, "Cleo", null, "minecraft:dirt");

		var standings = repository.standings(10);
		assertEquals(4, standings.score(Team.EAST));
		assertEquals(0, standings.score(Team.WEST));
		assertEquals(1, standings.score(Team.SOUTH));
		assertEquals(List.of("Alva", "Bo", "Cleo"), standings.top().stream().map(EventRepository.PlayerScore::username).toList());
		assertEquals(List.of(3, 2, 1), standings.top().stream().map(EventRepository.PlayerScore::items).toList());
		assertEquals(Team.SOUTH, standings.top().get(2).team());
		assertEquals(ALVA, standings.top().getFirst().uuid());

		assertEquals(List.of("Alva", "Bo"), repository.standings(2).top().stream().map(EventRepository.PlayerScore::username).toList());
	}

	@Test
	public void aPlayerPicksATeamOnce() throws SQLException {
		assertEquals(new PlayerInfo(Team.WEST, 0), repository.joinTeam(ALVA, "Alva", null, Team.WEST));
		repository.submit(ALVA, "Alva", null, "minecraft:dirt");
		assertEquals(new PlayerInfo(Team.WEST, 1), repository.joinTeam(ALVA, "Alva", null, Team.EAST), "the first pick stands");
		assertEquals(Optional.of(new PlayerInfo(Team.WEST, 1)), repository.loadPlayer(ALVA, "Alva", null));
		assertEquals(1, repository.standings(10).score(Team.WEST));
	}

	@Test
	public void movingAPlayerMovesTheirSubmissions() throws SQLException {
		repository.setTeam(ALVA, "Alva", null, Team.EAST);
		repository.submit(ALVA, "Alva", null, "minecraft:dirt");
		repository.submit(ALVA, "Alva", null, "minecraft:stone");

		assertEquals(2, repository.setTeam(ALVA, "Alva", null, Team.WEST));
		var standings = repository.standings(10);
		assertEquals(0, standings.score(Team.EAST));
		assertEquals(2, standings.score(Team.WEST));
		assertEquals(Optional.of(new PlayerInfo(Team.WEST, 2)), repository.loadPlayer(ALVA, "Alva", null));
		assertEquals(Outcome.ALREADY_SUBMITTED, repository.submit(ALVA, "Alva", null, "minecraft:dirt").outcome(), "still theirs after the move");
	}

	@Test
	public void aRenamedPlayerKeepsEverythingUnderTheNewName() throws SQLException {
		repository.setTeam(ALVA, "Alva", null, Team.EAST);
		repository.submit(ALVA, "Alva", null, "minecraft:dirt");

		assertEquals(Optional.of(new PlayerInfo(Team.EAST, 1)), repository.loadPlayer(ALVA, "Alva_Renamed", null));
		assertEquals("Alva_Renamed", repository.standings(10).top().getFirst().username());
		assertEquals(Outcome.ALREADY_SUBMITTED, repository.submit(ALVA, "Alva_Renamed", null, "minecraft:dirt").outcome());
	}

	/** The website draws heads from the stored skin hash: a known one replaces it, an unknown (null) or malformed one keeps it. */
	@Test
	public void theSkinFollowsThePlayerAndUnknownSkinsKeepTheStoredOne() throws SQLException {
		repository.joinTeam(ALVA, "Alva", "abc123", Team.EAST);
		assertEquals("abc123", skinOf(ALVA));

		repository.loadPlayer(ALVA, "Alva", null);
		assertEquals("abc123", skinOf(ALVA), "offline lookups don't know the skin");

		repository.submit(ALVA, "Alva", "def456", "minecraft:dirt");
		assertEquals("def456", skinOf(ALVA));

		repository.setTeam(ALVA, "Alva", null, Team.WEST);
		assertEquals("def456", skinOf(ALVA), "an admin moving an offline player keeps the skin");

		repository.loadPlayer(ALVA, "Alva", "'><script>");
		assertEquals("def456", skinOf(ALVA), "only a hex hash is stored");

		repository.setTeam(BO, "Bo", "not hex", Team.WEST);
		assertNull(skinOf(BO));
	}

	private String skinOf(UUID uuid) throws SQLException {
		try (Connection connection = connect(); var statement = connection.prepareStatement("SELECT skin FROM museum_players WHERE uuid = ?")) {
			statement.setString(1, uuid.toString());
			try (var rows = statement.executeQuery()) {
				return rows.next() ? rows.getString(1) : null;
			}
		}
	}

	/** Only the registry id is stored, so nothing but it tells two items apart; an id the column can't hold is refused, not cut. */
	@Test
	public void anItemIdTooLongForTheColumnIsRefused() throws SQLException {
		repository.setTeam(ALVA, "Alva", null, Team.EAST);
		String longest = "minecraft:" + "a".repeat(EventRepository.MAX_ITEM_ID_LENGTH - "minecraft:".length());
		assertEquals(Outcome.REGISTERED, repository.submit(ALVA, "Alva", null, longest).outcome());
		assertThrows(SQLException.class, () -> repository.submit(ALVA, "Alva", null, longest + "a"));
		assertEquals(Optional.of(new PlayerInfo(Team.EAST, 1)), repository.loadPlayer(ALVA, "Alva", null));
	}

	/** A double click, or two servers: the primary key lets exactly one of them in. */
	@Test
	public void concurrentSubmissionsOfOneItemRegisterItOnce() throws Exception {
		repository.setTeam(ALVA, "Alva", null, Team.EAST);
		int clicks = 16;
		try (var pool = Executors.newFixedThreadPool(clicks)) {
			Callable<Outcome> click = () -> repository.submit(ALVA, "Alva", null, "minecraft:dirt").outcome();
			var futures = pool.invokeAll(Collections.nCopies(clicks, click));
			int registered = 0;
			for (var future : futures) {
				Outcome outcome = future.get();
				if (outcome == Outcome.REGISTERED) {
					registered++;
				} else {
					assertEquals(Outcome.ALREADY_SUBMITTED, outcome);
				}
			}
			assertEquals(1, registered);
		}
		assertEquals(Optional.of(new PlayerInfo(Team.EAST, 1)), repository.loadPlayer(ALVA, "Alva", null));
	}

	/** Clicks with different items run side by side: each still gets its own count, so one of them is the 10th. */
	@Test
	public void concurrentSubmissionsOfDifferentItemsCountOneAfterTheOther() throws Exception {
		repository.setTeam(ALVA, "Alva", null, Team.EAST);
		int clicks = 16;
		var items = new java.util.ArrayList<Callable<Integer>>();
		for (int i = 0; i < clicks; i++) {
			String item = "minecraft:item_" + i;
			items.add(() -> repository.submit(ALVA, "Alva", null, item).playerItems());
		}
		var counts = new java.util.TreeSet<Integer>();
		try (var pool = Executors.newFixedThreadPool(clicks)) {
			for (var future : pool.invokeAll(items)) {
				counts.add(future.get());
			}
		}
		assertEquals(clicks, counts.size(), "every submission saw a different count: " + counts);
		assertEquals(clicks, counts.last());
		assertEquals(clicks, repository.submittedItems(ALVA).size());
	}

	@Test
	public void submittedItemsAreThePlayersOwn() throws SQLException {
		assertEquals(java.util.Set.of(), repository.submittedItems(ALVA));
		repository.setTeam(ALVA, "Alva", null, Team.EAST);
		repository.setTeam(BO, "Bo", null, Team.EAST);
		repository.submit(ALVA, "Alva", null, "minecraft:dirt");
		repository.submit(ALVA, "Alva", null, "minecraft:stone");
		repository.submit(BO, "Bo", null, "minecraft:gravel");
		assertEquals(java.util.Set.of("minecraft:dirt", "minecraft:stone"), repository.submittedItems(ALVA));
	}

	@Test
	public void theTablePrefixMustBeAPlainIdentifier() {
		assertThrows(IllegalArgumentException.class, () -> new EventRepository(this::connect, "museum; DROP TABLE x; --"));
	}
}
