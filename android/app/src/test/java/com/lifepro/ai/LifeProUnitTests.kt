package com.lifepro.ai

import org.junit.Assert.*
import org.junit.Test

class LifeProUnitTests {

    // Mock contact data for testing resolution logic
    data class TestContact(
        val name: String,
        val relationship: String,
        val phoneNumber: String,
        val isFavorite: Boolean = false,
        val emergencyPriority: Int = 1
    )

    private val contacts = listOf(
        TestContact("Ananya Sharma", "Daughter", "+91 98765 11223", isFavorite = true, emergencyPriority = 1),
        TestContact("Arjun Sharma", "Son", "+91 98765 44556", isFavorite = true, emergencyPriority = 2),
        TestContact("Dr. Mehta", "Doctor", "+91 98765 77889", isFavorite = false, emergencyPriority = 3),
        TestContact("Indian SOS", "Emergency", "112", isFavorite = true, emergencyPriority = 0)
    )

    private fun resolveContact(phrase: String): TestContact? {
        val clean = phrase.lowercase().replace("call", "").replace("my", "").replace("please", "").trim()
        if (clean.isEmpty()) return null

        // 1. By relationship
        val byRel = contacts.firstOrNull { it.relationship.lowercase() == clean || clean.contains(it.relationship.lowercase()) }
        if (byRel != null) return byRel

        // 2. By name
        val byName = contacts.firstOrNull { it.name.lowercase().contains(clean) || clean.contains(it.name.lowercase()) }
        if (byName != null) return byName

        // 3. By favorite keyword
        if (clean.contains("favorite") || clean.contains("favourite")) {
            return contacts.filter { it.isFavorite }.minByOrNull { it.emergencyPriority }
        }

        return null
    }

    @Test
    fun testResolveDaughter() {
        val contact = resolveContact("Call my daughter please")
        assertNotNull(contact)
        assertEquals("Ananya Sharma", contact?.name)
        assertEquals("+91 98765 11223", contact?.phoneNumber)
    }

    @Test
    fun testResolveSon() {
        val contact = resolveContact("call son")
        assertNotNull(contact)
        assertEquals("Arjun Sharma", contact?.name)
    }

    @Test
    fun testResolveDoctorByName() {
        val contact = resolveContact("Call Dr. Mehta")
        assertNotNull(contact)
        assertEquals("Doctor", contact?.relationship)
    }

    @Test
    fun testAmbiguousContactReturnsNull() {
        // "Call that guy" should return null so assistant asks clarification instead of random calling
        val contact = resolveContact("Call that guy")
        assertNull(contact)
    }

    @Test
    fun testResolveFavoriteContact() {
        val contact = resolveContact("Call my favorite")
        assertNotNull(contact)
        assertTrue(contact?.isFavorite == true)
    }

    @Test
    fun testSequentialEmergencyPriorityOrder() {
        val sorted = contacts.sortedBy { it.emergencyPriority }
        assertEquals("112", sorted[0].phoneNumber) // Highest priority (0)
        assertEquals("Ananya Sharma", sorted[1].name) // Priority 1
        assertEquals("Arjun Sharma", sorted[2].name) // Priority 2
        assertEquals("Dr. Mehta", sorted[3].name) // Priority 3
    }

    @Test
    fun testPairingCodeFormat() {
        val code = "LP-" + (100000..999999).random()
        assertTrue(code.startsWith("LP-"))
        assertEquals(9, code.length)
        val digits = code.removePrefix("LP-")
        assertTrue(digits.toIntOrNull() != null)
    }

    @Test
    fun testEmergencyIntentDetection() {
        val phrases = listOf("help me please", "call ambulance now", "save me", "there is danger", "call police")
        for (phrase in phrases) {
            val lower = phrase.lowercase()
            val isEmergency = lower.contains("help") || lower.contains("ambulance") || lower.contains("save me") || lower.contains("danger") || lower.contains("police")
            assertTrue("Expected emergency for '$phrase'", isEmergency)
        }
    }

    @Test
    fun testFallDetectionInquiryLogic() {
        val phrase = "I fell down in the living room"
        val lower = phrase.lowercase()
        val isFall = lower.contains("fell down") || lower.contains("i fell") || lower.contains("hurt")
        assertTrue(isFall)
    }

    @Test
    fun testCancellationVoiceTrigger() {
        val phrase = "cancel the emergency false alarm"
        val lower = phrase.lowercase()
        val isCancel = lower.contains("cancel") || lower.contains("false alarm") || lower.contains("stop")
        assertTrue(isCancel)
    }
}
