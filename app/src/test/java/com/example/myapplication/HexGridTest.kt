package com.example.myapplication

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class HexGridTest {
    /** 서버 apps/api/src/roadland/domain/rules.spec.ts의 VECTORS와 같은 값. 한쪽을 바꾸면 다른 쪽도 바꿔야 한다. */
    @Test fun matchesServerTiles() {
        val vectors = listOf(
            Triple(37.5446, 127.0559, "532866:240816"),
            Triple(37.4979, 127.0276, "532896:240466"),
            Triple(35.1796, 129.0756, "551970:223377"),
            Triple(0.0, 0.0, "0:0"),
            Triple(-33.8688, 151.2093, "884427:-213931"),
        )
        for ((lat, lng, id) in vectors) assertEquals("$lat,$lng", id, HexGrid.tileFor(lat, lng))
    }

    @Test fun nearbyPointsShareATile() {
        assertEquals(HexGrid.tileFor(37.5446, 127.0559), HexGrid.tileFor(37.54461, 127.05591))
        assertNotEquals(HexGrid.tileFor(37.5446, 127.0559), HexGrid.tileFor(37.5460, 127.0559))
    }
}
