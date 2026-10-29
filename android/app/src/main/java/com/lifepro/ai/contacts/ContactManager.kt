package com.lifepro.ai.contacts

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class SafetyContact(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val relationship: String, // e.g., "Daughter", "Son", "Doctor", "Spouse", "Caregiver"
    val phoneNumber: String,
    val isFavorite: Boolean = false,
    val emergencyPriority: Int = 1 // 1 = highest emergency priority
)

class ContactManager private constructor(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("lifepro_contacts_prefs", Context.MODE_PRIVATE)
    private val contactsList = mutableListOf<SafetyContact>()

    companion object {
        @Volatile
        private var instance: ContactManager? = null

        fun getInstance(context: Context): ContactManager {
            return instance ?: synchronized(this) {
                instance ?: ContactManager(context.applicationContext).also { instance = it }
            }
        }

        /**
         * Validates that a phone number contains a reasonable digit count (3 to 15 digits)
         * to support both short emergency codes (e.g., 112, 911) and full international numbers.
         */
        fun isValidPhoneNumber(phoneNumber: String): Boolean {
            val digits = phoneNumber.filter { it.isDigit() }
            return digits.length in 3..15
        }
    }

    init {
        loadContacts()
    }

    fun getAllContacts(): List<SafetyContact> {
        return contactsList.toList()
    }

    fun getFavoriteContacts(): List<SafetyContact> {
        return contactsList.filter { it.isFavorite }
    }

    fun getEmergencyFallbackQueue(): List<SafetyContact> {
        return contactsList.sortedBy { it.emergencyPriority }
    }

    private fun loadContacts() {
        contactsList.clear()
        val raw = prefs.getString("contacts_json_array", null)
        if (raw != null) {
            try {
                val array = JSONArray(raw)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    contactsList.add(
                        SafetyContact(
                            id = obj.optString("id", UUID.randomUUID().toString()),
                            name = obj.getString("name"),
                            relationship = obj.optString("relationship", "Contact"),
                            phoneNumber = obj.getString("phoneNumber"),
                            isFavorite = obj.optBoolean("isFavorite", false),
                            emergencyPriority = obj.optInt("emergencyPriority", 1)
                        )
                    )
                }
            } catch (e: Exception) {
                populateDefaultContacts()
            }
        } else {
            populateDefaultContacts()
        }
    }

    private fun populateDefaultContacts() {
        contactsList.clear()
        contactsList.add(SafetyContact(name = "Ananya Sharma", relationship = "Daughter", phoneNumber = "+91 98765 11223", isFavorite = true, emergencyPriority = 1))
        contactsList.add(SafetyContact(name = "Arjun Sharma", relationship = "Son", phoneNumber = "+91 98765 44556", isFavorite = true, emergencyPriority = 2))
        contactsList.add(SafetyContact(name = "Dr. Mehta", relationship = "Doctor", phoneNumber = "+91 98765 77889", isFavorite = false, emergencyPriority = 3))
        contactsList.add(SafetyContact(name = "Indian National SOS", relationship = "Emergency", phoneNumber = "112", isFavorite = true, emergencyPriority = 0))
        saveContacts()
    }

    fun saveContacts() {
        val array = JSONArray()
        for (c in contactsList) {
            val obj = JSONObject().apply {
                put("id", c.id)
                put("name", c.name)
                put("relationship", c.relationship)
                put("phoneNumber", c.phoneNumber)
                put("isFavorite", c.isFavorite)
                put("emergencyPriority", c.emergencyPriority)
            }
            array.put(obj)
        }
        prefs.edit().putString("contacts_json_array", array.toString()).apply()
    }

    /**
     * Adds a contact after validating that the phone number is valid and the name is non-empty.
     */
    fun addContact(contact: SafetyContact): Boolean {
        if (!isValidPhoneNumber(contact.phoneNumber) || contact.name.trim().isEmpty()) {
            return false
        }
        contactsList.add(contact)
        saveContacts()
        return true
    }

    fun removeContact(id: String) {
        contactsList.removeAll { it.id == id }
        saveContacts()
    }

    /**
     * Resolves natural language commands like:
     * "Call my daughter" -> matches relationship "daughter"
     * "Call Dr. Mehta" -> matches name "Dr. Mehta"
     * "Call Rahul" -> matches name "Rahul"
     * "Call my favorite" -> returns highest priority favorite contact
     */
    fun resolveContactFromSpeech(spokenPhrase: String): SafetyContact? {
        val clean = spokenPhrase.lowercase().replace("call", "").replace("my", "").replace("please", "").trim()
        if (clean.isEmpty()) return null

        // 1. Direct relationship match
        val byRelationship = contactsList.firstOrNull {
            it.relationship.lowercase() == clean || clean.contains(it.relationship.lowercase())
        }
        if (byRelationship != null) return byRelationship

        // 2. Direct name match
        val byName = contactsList.firstOrNull {
            it.name.lowercase().contains(clean) || clean.contains(it.name.lowercase())
        }
        if (byName != null) return byName

        // 3. "Favorite" keyword
        if (clean.contains("favorite") || clean.contains("favourite")) {
            return getFavoriteContacts().firstOrNull() ?: contactsList.firstOrNull()
        }

        return null
    }
}
