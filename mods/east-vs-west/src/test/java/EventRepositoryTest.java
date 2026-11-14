import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import se.metacraft.east_vs_west.Team;
import se.metacraft.east_vs_west.db.EventRepository;
import se.metacraft.east_vs_west.db.EventRepository.Outcome;
import se.metacraft.east_vs_west.db.EventRepository.PlayerInfo;

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
		url = "jdbc:h2:mem:evw" + DATABASES.incrementAndGet() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
		repository = new EventRepository(this::connect, "evw_");
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
		repository.setTeam(ALVA, "Alva", Team.EAST);
		repository.submit(ALVA, "Alva", "minecraft:dirt");
		repository.createTables();
		assertEquals(Optional.of(new PlayerInfo(Team.EAST, 1)), repository.loadPlayer(ALVA, "Alva"));
	}

	@Test
	public void aPlayerOnNoTeamCannotSubmit() throws SQLException {
		assertEquals(Optional.empty(), repository.loadPlayer(ALVA, "Alva"));
		var result = repository.submit(ALVA, "Alva", "minecraft:dirt");
		assertEquals(Outcome.NO_TEAM, result.outcome());
		assertNull(result.team());
		assertEquals(0, repository.standings(10).top().size());
	}

	@Test
	public void aPlayerSubmitsAnItemOnce() throws SQLException {
		repository.setTeam(ALVA, "Alva", Team.EAST);
		var first = repository.submit(ALVA, "Alva", "minecraft:diamond_sword");
		assertEquals(Outcome.REGISTERED, first.outcome());
		assertEquals(Team.EAST, first.team());
		assertTrue(first.firstForTeam());
		assertEquals(1, first.playerItems());
		assertEquals(1, first.teamItems());

		var again = repository.submit(ALVA, "Alva", "minecraft:diamond_sword");
		assertEquals(Outcome.ALREADY_SUBMITTED, again.outcome());
		assertFalse(again.firstForTeam());
		assertEquals(1, again.playerItems());
		assertEquals(1, again.teamItems());
	}

	@Test
	public void anyPlayerMaySubmitAnItemSomeoneElseHas() throws SQLException {
		repository.setTeam(ALVA, "Alva", Team.EAST);
		repository.setTeam(BO, "Bo", Team.EAST);
		repository.setTeam(CLEO, "Cleo", Team.WEST);
		repository.submit(ALVA, "Alva", "minecraft:dirt");

		var teammate = repository.submit(BO, "Bo", "minecraft:dirt");
		assertEquals(Outcome.REGISTERED, teammate.outcome());
		assertFalse(teammate.firstForTeam(), "the team already had it");
		assertEquals(1, teammate.playerItems());
		assertEquals(1, teammate.teamItems(), "a team's score counts an item once");

		var rival = repository.submit(CLEO, "Cleo", "minecraft:dirt");
		assertEquals(Outcome.REGISTERED, rival.outcome());
		assertTrue(rival.firstForTeam(), "new for the other team");
		assertEquals(1, rival.teamItems());
	}

	@Test
	public void standingsCountDistinctItemsPerTeamAndRankPlayers() throws SQLException {
		repository.setTeam(ALVA, "Alva", Team.EAST);
		repository.setTeam(BO, "Bo", Team.EAST);
		repository.setTeam(CLEO, "Cleo", Team.REST);
		for (String item : List.of("minecraft:dirt", "minecraft:stone", "minecraft:sand")) {
			repository.submit(ALVA, "Alva", item);
		}
		repository.submit(BO, "Bo", "minecraft:dirt");
		repository.submit(BO, "Bo", "minecraft:gravel");
		repository.submit(CLEO, "Cleo", "minecraft:dirt");

		var standings = repository.standings(10);
		assertEquals(4, standings.score(Team.EAST));
		assertEquals(0, standings.score(Team.WEST));
		assertEquals(1, standings.score(Team.REST));
		assertEquals(List.of("Alva", "Bo", "Cleo"), standings.top().stream().map(EventRepository.PlayerScore::username).toList());
		assertEquals(List.of(3, 2, 1), standings.top().stream().map(EventRepository.PlayerScore::items).toList());
		assertEquals(Team.REST, standings.top().get(2).team());
		assertEquals(ALVA, standings.top().getFirst().uuid());

		assertEquals(List.of("Alva", "Bo"), repository.standings(2).top().stream().map(EventRepository.PlayerScore::username).toList());
	}

	@Test
	public void aPlayerPicksATeamOnce() throws SQLException {
		assertEquals(new PlayerInfo(Team.WEST, 0), repository.joinTeam(ALVA, "Alva", Team.WEST));
		repository.submit(ALVA, "Alva", "minecraft:dirt");
		assertEquals(new PlayerInfo(Team.WEST, 1), repository.joinTeam(ALVA, "Alva", Team.EAST), "the first pick stands");
		assertEquals(Optional.of(new PlayerInfo(Team.WEST, 1)), repository.loadPlayer(ALVA, "Alva"));
		assertEquals(1, repository.standings(10).score(Team.WEST));
	}

	@Test
	public void movingAPlayerMovesTheirSubmissions() throws SQLException {
		repository.setTeam(ALVA, "Alva", Team.EAST);
		repository.submit(ALVA, "Alva", "minecraft:dirt");
		repository.submit(ALVA, "Alva", "minecraft:stone");

		assertEquals(2, repository.setTeam(ALVA, "Alva", Team.WEST));
		var standings = repository.standings(10);
		assertEquals(0, standings.score(Team.EAST));
		assertEquals(2, standings.score(Team.WEST));
		assertEquals(Optional.of(new PlayerInfo(Team.WEST, 2)), repository.loadPlayer(ALVA, "Alva"));
		assertEquals(Outcome.ALREADY_SUBMITTED, repository.submit(ALVA, "Alva", "minecraft:dirt").outcome(), "still theirs after the move");
	}

	@Test
	public void aRenamedPlayerKeepsEverythingUnderTheNewName() throws SQLException {
		repository.setTeam(ALVA, "Alva", Team.EAST);
		repository.submit(ALVA, "Alva", "minecraft:dirt");

		assertEquals(Optional.of(new PlayerInfo(Team.EAST, 1)), repository.loadPlayer(ALVA, "Alva_Renamed"));
		assertEquals("Alva_Renamed", repository.standings(10).top().getFirst().username());
		assertEquals(Outcome.ALREADY_SUBMITTED, repository.submit(ALVA, "Alva_Renamed", "minecraft:dirt").outcome());
	}

	/** Only the registry id is stored, so nothing but it tells two items apart; an id the column can't hold is refused, not cut. */
	@Test
	public void anItemIdTooLongForTheColumnIsRefused() throws SQLException {
		repository.setTeam(ALVA, "Alva", Team.EAST);
		String longest = "minecraft:" + "a".repeat(EventRepository.MAX_ITEM_ID_LENGTH - "minecraft:".length());
		assertEquals(Outcome.REGISTERED, repository.submit(ALVA, "Alva", longest).outcome());
		assertThrows(SQLException.class, () -> repository.submit(ALVA, "Alva", longest + "a"));
		assertEquals(Optional.of(new PlayerInfo(Team.EAST, 1)), repository.loadPlayer(ALVA, "Alva"));
	}

	/** A double click, or two servers: the primary key lets exactly one of them in. */
	@Test
	public void concurrentSubmissionsOfOneItemRegisterItOnce() throws Exception {
		repository.setTeam(ALVA, "Alva", Team.EAST);
		int clicks = 16;
		try (var pool = Executors.newFixedThreadPool(clicks)) {
			Callable<Outcome> click = () -> repository.submit(ALVA, "Alva", "minecraft:dirt").outcome();
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
		assertEquals(Optional.of(new PlayerInfo(Team.EAST, 1)), repository.loadPlayer(ALVA, "Alva"));
	}

	@Test
	public void theTablePrefixMustBeAPlainIdentifier() {
		assertThrows(IllegalArgumentException.class, () -> new EventRepository(this::connect, "evw; DROP TABLE x; --"));
	}
}
