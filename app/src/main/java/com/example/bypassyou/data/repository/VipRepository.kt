package com.example.bypassyou.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.telephony.PhoneNumberUtils
import android.util.Log
import com.example.bypassyou.data.model.VipContact
import org.json.JSONArray
import org.json.JSONObject

class VipRepository(private val context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getVipContacts(): List<VipContact> {
        val jsonString = prefs.getString(KEY_CONTACTS, "[]") ?: "[]"
        val list = mutableListOf<VipContact>()
        try {
            val jsonArray = JSONArray(jsonString)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(
                    VipContact(
                        id = obj.optString("id", ""),
                        name = obj.optString("name", ""),
                        phoneNumber = obj.optString("phoneNumber", ""),
                        photoUri = if (obj.has("photoUri")) obj.getString("photoUri") else null
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing saved VIP contacts", e)
        }
        return list
    }

    fun addVipContact(contact: VipContact) {
        val current = getVipContacts().toMutableList()
        current.removeAll { it.id == contact.id || isSamePhoneNumber(it.phoneNumber, contact.phoneNumber) }
        current.add(0, contact)
        saveVipContacts(current)
        Log.d(TAG, "Saved VIP contact: '${contact.name}' (${contact.phoneNumber})")
    }

    fun removeVipContact(contactId: String) {
        val current = getVipContacts().filterNot { it.id == contactId }
        saveVipContacts(current)
        Log.d(TAG, "Removed VIP contact with id: $contactId")
    }

    fun isVipNumber(rawIncomingNumber: String?): Boolean {
        if (rawIncomingNumber.isNull_or_blank()) {
            return false
        }

        val incoming = rawIncomingNumber!!
        val currentVips = getVipContacts()

        return currentVips.any { vip ->
            isSamePhoneNumber(incoming, vip.phoneNumber)
        }
    }

    private fun saveVipContacts(contacts: List<VipContact>) {
        val jsonArray = JSONArray()
        contacts.forEach { contact ->
            val obj = JSONObject().apply {
                put("id", contact.id)
                put("name", contact.name)
                put("phoneNumber", contact.phoneNumber)
                contact.photoUri?.let { put("photoUri", it) }
            }
            jsonArray.put(obj)
        }
        prefs.edit().putString(KEY_CONTACTS, jsonArray.toString()).commit()
    }

    companion object {
        private const val TAG = "VipRepository"
        private const val PREFS_NAME = "bypass_you_vips"
        private const val KEY_CONTACTS = "vip_contacts_list"

        fun normalizeDigits(number: String): String {
            return number.replace(Regex("[^0-9]"), "")
        }

        fun isSamePhoneNumber(num1: String, num2: String): Boolean {
            val s1 = num1.trim()
            val s2 = num2.trim()
            if (s1.isEmpty() || s2.isEmpty()) return false

            try {
                if (PhoneNumberUtils.compare(s1, s2)) {
                    return true
                }
            } catch (e: Exception) {
            }

            if (s1 == s2) return true

            val d1 = normalizeDigits(s1)
            val d2 = normalizeDigits(s2)
            if (d1.isEmpty() || d2.isEmpty()) return false
            if (d1 == d2) return true

            val minMatchLength = 7
            val minCompare = minOf(d1.length, d2.length, 10)
            if (minCompare >= minMatchLength) {
                val tail1 = d1.takeLast(minCompare)
                val tail2 = d2.takeLast(minCompare)
                if (tail1 == tail2) return true
            }

            return false
        }
    }
}

private fun String?.isNull_or_blank(): Boolean = this == null || this.trim().isEmpty()
