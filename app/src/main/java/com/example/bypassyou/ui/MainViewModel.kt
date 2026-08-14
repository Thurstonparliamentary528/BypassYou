package com.example.bypassyou.ui

import android.app.Application
import android.content.ContentResolver
import android.net.Uri
import android.provider.ContactsContract
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import com.example.bypassyou.data.model.VipContact
import com.example.bypassyou.data.repository.SettingsRepository
import com.example.bypassyou.data.repository.VipRepository
import com.example.bypassyou.service.AudioBypassManager
import com.example.bypassyou.service.BypassForegroundService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val vipRepository = VipRepository(application)
    private val settingsRepository = SettingsRepository(application)
    private val audioBypassManager = AudioBypassManager.getInstance(application)

    private val _vipContacts = MutableStateFlow<List<VipContact>>(emptyList())
    val vipContacts: StateFlow<List<VipContact>> = _vipContacts.asStateFlow()

    private val _isBypassEnabled = MutableStateFlow(settingsRepository.isBypassEnabled)
    val isBypassEnabled: StateFlow<Boolean> = _isBypassEnabled.asStateFlow()

    private val _targetVolumePercent = MutableStateFlow(settingsRepository.targetVolumePercent)
    val targetVolumePercent: StateFlow<Int> = _targetVolumePercent.asStateFlow()

    private val _themeMode = MutableStateFlow(settingsRepository.themeMode)
    val themeMode: StateFlow<String> = _themeMode.asStateFlow()

    init {
        loadVipContacts()
        if (settingsRepository.isBypassEnabled) {
            try {
                BypassForegroundService.start(getApplication())
            } catch (e: Exception) {
                Log.w("MainViewModel", "Could not start ForegroundService automatically", e)
            }
        }
    }

    fun loadVipContacts() {
        _vipContacts.value = vipRepository.getVipContacts()
    }

    fun toggleBypassEnabled(enabled: Boolean) {
        settingsRepository.isBypassEnabled = enabled
        _isBypassEnabled.value = enabled

        try {
            if (enabled) {
                BypassForegroundService.start(getApplication())
            } else {
                BypassForegroundService.stop(getApplication())
            }
        } catch (e: Exception) {
            Log.w("MainViewModel", "Error toggling foreground service", e)
        }
    }

    fun setTargetVolumePercent(percent: Int) {
        val clamped = percent.coerceIn(0, 100)
        settingsRepository.targetVolumePercent = clamped
        _targetVolumePercent.value = clamped
        audioBypassManager.updatePreviewVolume(clamped)
    }

    fun setThemeMode(mode: String) {
        settingsRepository.themeMode = mode
        _themeMode.value = mode
    }

    fun removeVipContact(contactId: String) {
        vipRepository.removeVipContact(contactId)
        loadVipContacts()
    }

    fun addManualContact(name: String, number: String) {
        val cleanNumber = number.trim()
        if (cleanNumber.isNotEmpty()) {
            val contact = VipContact(
                id = UUID.randomUUID().toString(),
                name = name.trim().ifBlank { cleanNumber },
                phoneNumber = cleanNumber
            )
            vipRepository.addVipContact(contact)
            loadVipContacts()
        }
    }

    fun processPickedContactUri(contactUri: Uri) {
        val resolver: ContentResolver = getApplication<Application>().contentResolver
        var name = ""
        var phoneNumber = ""
        var photoUri: String? = null

        try {
            resolver.query(
                contactUri,
                arrayOf(
                    ContactsContract.Contacts._ID,
                    ContactsContract.Contacts.DISPLAY_NAME,
                    ContactsContract.Contacts.PHOTO_THUMBNAIL_URI,
                    ContactsContract.Contacts.HAS_PHONE_NUMBER
                ),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idIdx = cursor.getColumnIndex(ContactsContract.Contacts._ID)
                    val nameIdx = cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)
                    val photoIdx = cursor.getColumnIndex(ContactsContract.Contacts.PHOTO_THUMBNAIL_URI)
                    val hasPhoneIdx = cursor.getColumnIndex(ContactsContract.Contacts.HAS_PHONE_NUMBER)

                    val contactId = if (idIdx >= 0) cursor.getString(idIdx) else null
                    if (nameIdx >= 0) name = cursor.getString(nameIdx) ?: ""
                    if (photoIdx >= 0) photoUri = cursor.getString(photoIdx)
                    val hasPhone = if (hasPhoneIdx >= 0) cursor.getInt(hasPhoneIdx) else 0

                    if (hasPhone > 0 && !contactId.isNullOrEmpty()) {
                        resolver.query(
                            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                            arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
                            "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
                            arrayOf(contactId),
                            null
                        )?.use { phoneCursor ->
                            if (phoneCursor.moveToFirst()) {
                                val numIdx = phoneCursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                                if (numIdx >= 0) {
                                    phoneNumber = phoneCursor.getString(numIdx) ?: ""
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("MainViewModel", "Error in primary contact query", e)
        }

        if (phoneNumber.isEmpty()) {
            try {
                resolver.query(contactUri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        for (i in 0 until cursor.columnCount) {
                            val colName = cursor.getColumnName(i).lowercase()
                            if (colName.contains("data1") || colName.contains("number") || colName.contains("phone")) {
                                val v = cursor.getString(i)
                                if (!v.isNullOrEmpty() && v.any { it.isDigit() }) {
                                    phoneNumber = v
                                    break
                                }
                            }
                        }
                        if (name.isEmpty()) {
                            val nameIdx = cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)
                            if (nameIdx >= 0) name = cursor.getString(nameIdx) ?: ""
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error in fallback contact query", e)
            }
        }

        if (phoneNumber.isNotBlank()) {
            val vip = VipContact(
                id = UUID.randomUUID().toString(),
                name = name.ifBlank { phoneNumber },
                phoneNumber = phoneNumber,
                photoUri = photoUri
            )
            vipRepository.addVipContact(vip)
            loadVipContacts()
        }
    }

    fun playSoundPreview() {
        audioBypassManager.playPreviewSound()
    }
}
