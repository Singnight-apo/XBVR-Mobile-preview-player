package top.liuwei.xbvr.domain;

import org.junit.Test;
import static org.junit.Assert.*;

/** Characterisation of the poster-wall cover-ratio rules that previously lived in MainActivity. */
public class CoverRatioPolicyTest {
    @Test public void invalidCacheDoesNotChangeTheDefault() {
        assertEquals(16f / 9f, CoverRatioPolicy.resolve(0, Float.NaN), 0f);
        assertEquals(16f / 9f, CoverRatioPolicy.resolve(0, -1f), 0f);
        assertEquals(3f / 2f, CoverRatioPolicy.resolve(2, Float.POSITIVE_INFINITY), 0f);
        assertFalse(CoverRatioPolicy.changed(1f, 1f));
        assertTrue(CoverRatioPolicy.crop(1));
        assertFalse(CoverRatioPolicy.crop(0));
    }

    @Test public void modeClampsOutOfRangeValuesToAutomatic() {
        assertEquals(0, CoverRatioPolicy.mode(-1));
        assertEquals(0, CoverRatioPolicy.mode(4));
        assertEquals(0, CoverRatioPolicy.mode(Integer.MIN_VALUE));
        assertEquals(3, CoverRatioPolicy.mode(3));
        assertEquals(2, CoverRatioPolicy.mode(2));
    }

    @Test public void fixedRatioMatchesTheOldManualMapping() {
        assertEquals(1f, CoverRatioPolicy.fixed(1), 0f);
        assertEquals(3f / 2f, CoverRatioPolicy.fixed(2), 0f);
        assertEquals(16f / 9f, CoverRatioPolicy.fixed(0), 0f);
        assertEquals(16f / 9f, CoverRatioPolicy.fixed(3), 0f);
        assertEquals(CoverRatioPolicy.DEFAULT, CoverRatioPolicy.fixed(-5), 0f);
    }

    @Test public void validRejectsNonPositiveAndNonFiniteRatios() {
        assertFalse(CoverRatioPolicy.valid(Float.NaN));
        assertFalse(CoverRatioPolicy.valid(Float.POSITIVE_INFINITY));
        assertFalse(CoverRatioPolicy.valid(Float.NEGATIVE_INFINITY));
        assertFalse(CoverRatioPolicy.valid(0f));
        assertFalse(CoverRatioPolicy.valid(-.5f));
        assertTrue(CoverRatioPolicy.valid(.5f));
        assertTrue(CoverRatioPolicy.valid(1.7777778f));
    }

    @Test public void resolveUsesTheCacheOnlyInAutomaticMode() {
        assertEquals(1.5f, CoverRatioPolicy.resolve(0, 1.5f), 0f);
        assertEquals(1f, CoverRatioPolicy.resolve(1, 1.5f), 0f);
        assertEquals(3f / 2f, CoverRatioPolicy.resolve(2, 1.5f), 0f);
        assertEquals(16f / 9f, CoverRatioPolicy.resolve(3, 1.5f), 0f);
        assertEquals(16f / 9f, CoverRatioPolicy.resolve(0, 0f), 0f);
        assertEquals(16f / 9f, CoverRatioPolicy.resolve(9, Float.NaN), 0f);
    }

    @Test public void changedKeepsTheOldEpsilonBoundary() {
        assertFalse(CoverRatioPolicy.changed(1f, 1f));
        assertFalse(CoverRatioPolicy.changed(1f, 1f + .00005f));
        assertTrue(CoverRatioPolicy.changed(1f, 1f + .0002f));
        assertTrue(CoverRatioPolicy.changed(16f / 9f, 3f / 2f));
        assertFalse(CoverRatioPolicy.changed(1f, Float.NaN));
        assertFalse(CoverRatioPolicy.changed(1f, 0f));
        assertFalse(CoverRatioPolicy.changed(1f, -2f));
        assertFalse(CoverRatioPolicy.changed(0f, 0f));
        assertTrue(CoverRatioPolicy.changed(0f, 1f));
    }

    @Test public void cropOnlyForFixedRatios() {
        assertFalse(CoverRatioPolicy.crop(0));
        assertTrue(CoverRatioPolicy.crop(1));
        assertTrue(CoverRatioPolicy.crop(2));
        assertTrue(CoverRatioPolicy.crop(3));
    }
}
