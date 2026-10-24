package metacraft.kultur;

import se.metacraft.kultur.drinks.Alcohol;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The meter's arithmetic: Brewery's numbers at quality 10, its stages in steps of ten. */
class AlcoholTest {
	@Test
	void steps() {
		assertEquals(0, Alcohol.step(0));
		assertEquals(0, Alcohol.step(-3));
		assertEquals(1, Alcohol.step(0.1));
		assertEquals(1, Alcohol.step(29.9));
		assertEquals(2, Alcohol.step(30));
		assertEquals(3, Alcohol.step(40));
		assertEquals(4, Alcohol.step(50));
		assertEquals(5, Alcohol.step(60));
		assertEquals(6, Alcohol.step(70));
		assertEquals(7, Alcohol.step(80));
		assertEquals(7, Alcohol.step(99));
	}

	@Test
	void drinking() {
		assertEquals(20, Alcohol.afterDrink(-5, 20));
		assertEquals(105, Alcohol.afterDrink(70, 35));
		assertEquals(50, Alcohol.afterDrink(30, 20));
		assertTrue(Alcohol.blacksOut(100));
		assertFalse(Alcohol.blacksOut(99.9));
	}

	@Test
	void draining() {
		assertEquals(0, Alcohol.afterTicks(1, 100));
		assertEquals(45.6, Alcohol.afterTicks(60, 1200), 1e-9);
		assertEquals(1000, Alcohol.ticksUntilSober(12));
		assertEquals(0, Alcohol.ticksUntilSober(0));
	}

	@Test
	void stagger() {
		assertEquals(0.042, Alcohol.staggerLength(50, 0), 1e-9);
		assertEquals(0.2, Alcohol.staggerLength(90, 1));
		assertEquals(0.2, Alcohol.staggerLength(99, 0.5));
	}

	@Test
	void drinkAmplifier() {
		assertEquals(0, Alcohol.drinkAmplifier(20));
		assertEquals(0, Alcohol.drinkAmplifier(60));
		assertEquals(1, Alcohol.drinkAmplifier(70));
		assertEquals(1, Alcohol.drinkAmplifier(200));
	}

	@Test
	void sobering() {
		assertEquals(0, Alcohol.sobered(2, 3));
		assertEquals(0, Alcohol.sobered(0, 3));
		assertEquals(-1, Alcohol.sobered(-1, 3));
		assertEquals(17, Alcohol.sobered(20, 3));
	}
}
