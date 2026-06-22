package com.autoreply.app

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var switchEnabled: SwitchCompat
    private lateinit var editMessage: EditText
    private lateinit var btnAddContact: Button
    private lateinit var btnManageList: Button
    private lateinit var btnSaveMessage: Button
    private lateinit var tvCount: TextView

    private val targetContacts = mutableListOf<TargetContact>()

    companion object {
        const val PREFS_NAME = "AutoReplyPrefs"
        const val KEY_ENABLED = "enabled"
        const val KEY_MESSAGE = "message"
        const val KEY_CONTACTS = "contacts"
        const val DEFAULT_MESSAGE = "지금 통화가 어렵습니다. 문자 남겨주세요."
        const val REQ_PERMISSIONS = 1001
        const val REQ_PICK_CONTACT = 1002
        const val REQ_CALL_SCREENING_ROLE = 1003
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        switchEnabled = findViewById(R.id.switchEnabled)
        editMessage = findViewById(R.id.editMessage)
        btnAddContact = findViewById(R.id.btnAddContact)
        btnManageList = findViewById(R.id.btnManageList)
        btnSaveMessage = findViewById(R.id.btnSaveMessage)
        tvCount = findViewById(R.id.tvCount)

        switchEnabled.isChecked = prefs.getBoolean(KEY_ENABLED, false)
        switchEnabled.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(KEY_ENABLED, isChecked).apply()
            if (isChecked) {
                checkPermissions()
                requestCallScreeningRole()
            }
        }

        editMessage.setText(prefs.getString(KEY_MESSAGE, DEFAULT_MESSAGE))
        btnSaveMessage.setOnClickListener {
            prefs.edit().putString(KEY_MESSAGE, editMessage.text.toString()).apply()
            Toast.makeText(this, "메시지가 저장되었습니다", Toast.LENGTH_SHORT).show()
        }

        btnAddContact.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS)
                == PackageManager.PERMISSION_GRANTED
            ) {
                openContactPicker()
            } else {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.READ_CONTACTS),
                    REQ_PERMISSIONS
                )
            }
        }

        btnManageList.setOnClickListener {
            startActivity(Intent(this, TargetListActivity::class.java))
        }

        checkPermissions()

        if (switchEnabled.isChecked) {
            requestCallScreeningRole()
        }
    }

    override fun onResume() {
        super.onResume()
        // 관리 화면에서 삭제하고 돌아왔을 때 카운트 새로고침
        loadContacts()
        updateCount()
    }

    private fun requestCallScreeningRole() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(Context.ROLE_SERVICE) as? RoleManager
            if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)) {
                if (!roleManager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)) {
                    val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING)
                    AlertDialog.Builder(this)
                        .setTitle("권한이 필요해요")
                        .setMessage("자동 거절 기능을 사용하려면 이 앱을 '통화 차단/거부' 앱으로 지정해야 합니다.\n\n다음 화면에서 허용해 주세요.")
                        .setPositiveButton("확인") { _, _ ->
                            startActivityForResult(intent, REQ_CALL_SCREENING_ROLE)
                        }
                        .setNegativeButton("취소", null)
                        .show()
                }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_PICK_CONTACT && resultCode == RESULT_OK && data != null) {
            val names = data.getStringArrayListExtra("names") ?: arrayListOf()
            val numbers = data.getStringArrayListExtra("numbers") ?: arrayListOf()
            loadContacts()
            var added = 0
            var skipped = 0
            for (i in names.indices) {
                if (addContactSilent(names[i], numbers[i])) added++ else skipped++
            }
            saveContacts()
            updateCount()
            val msg = if (skipped == 0) "${added}명 추가됨"
                      else "${added}명 추가, ${skipped}명은 이미 등록됨"
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        } else if (requestCode == REQ_CALL_SCREENING_ROLE) {
            if (resultCode == RESULT_OK) {
                Toast.makeText(this, "✓ 자동 응답 기능이 활성화되었습니다", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this, "권한이 거부되어 기능이 작동하지 않을 수 있습니다", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun checkPermissions() {
        val needed = mutableListOf<String>()
        val perms = mutableListOf(
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.SEND_SMS,
            Manifest.permission.READ_CALL_LOG
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        for (p in perms) {
            if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED) {
                needed.add(p)
            }
        }
        if (needed.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, needed.toTypedArray(), REQ_PERMISSIONS)
        }
    }

    private fun openContactPicker() {
        val intent = Intent(this, ContactPickerActivity::class.java)
        startActivityForResult(intent, REQ_PICK_CONTACT)
    }

    private fun addContactSilent(name: String, number: String): Boolean {
        val normalized = number.replace("-", "").replace(" ", "")
        if (targetContacts.any { it.number.replace("-", "").replace(" ", "") == normalized }) {
            return false
        }
        targetContacts.add(TargetContact(name, number))
        return true
    }

    private fun saveContacts() {
        val arr = JSONArray()
        for (c in targetContacts) {
            val obj = JSONObject()
            obj.put("name", c.name)
            obj.put("number", c.number)
            arr.put(obj)
        }
        prefs.edit().putString(KEY_CONTACTS, arr.toString()).apply()
    }

    private fun loadContacts() {
        val json = prefs.getString(KEY_CONTACTS, "[]") ?: "[]"
        val arr = JSONArray(json)
        targetContacts.clear()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            targetContacts.add(TargetContact(obj.getString("name"), obj.getString("number")))
        }
    }

    private fun updateCount() {
        tvCount.text = "${targetContacts.size}명 등록됨"
    }
}

data class TargetContact(val name: String, val number: String)
