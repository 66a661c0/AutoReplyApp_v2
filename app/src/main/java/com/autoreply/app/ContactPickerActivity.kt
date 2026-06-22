package com.autoreply.app

import android.app.Activity
import android.content.Intent
import android.database.Cursor
import android.os.Bundle
import android.provider.ContactsContract
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class ContactPickerActivity : AppCompatActivity() {

    private val allContacts = mutableListOf<PickerContact>()
    private val filtered = mutableListOf<PickerContact>()
    private lateinit var adapter: PickerAdapter
    private lateinit var btnSelectAll: Button
    private lateinit var btnDone: Button
    private lateinit var tvSelectedCount: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_picker)

        val recycler: RecyclerView = findViewById(R.id.recyclerPicker)
        val searchEdit: EditText = findViewById(R.id.editSearch)
        btnSelectAll = findViewById(R.id.btnSelectAll)
        btnDone = findViewById(R.id.btnDone)
        tvSelectedCount = findViewById(R.id.tvSelectedCount)

        loadContacts()
        filtered.addAll(allContacts)

        adapter = PickerAdapter(filtered) {
            updateSelectedCount()
            updateSelectAllButton()
        }
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter

        updateSelectedCount()
        updateSelectAllButton()

        searchEdit.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun afterTextChanged(s: Editable?) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val q = s?.toString()?.trim() ?: ""
                filtered.clear()
                if (q.isEmpty()) {
                    filtered.addAll(allContacts)
                } else {
                    filtered.addAll(allContacts.filter {
                        it.name.contains(q, ignoreCase = true) || it.number.contains(q)
                    })
                }
                adapter.notifyDataSetChanged()
                updateSelectAllButton()
            }
        })

        btnSelectAll.setOnClickListener {
            val allSelected = filtered.all { it.selected }
            val newState = !allSelected
            for (c in filtered) c.selected = newState
            adapter.notifyDataSetChanged()
            updateSelectedCount()
            updateSelectAllButton()
        }

        btnDone.setOnClickListener {
            val selected = allContacts.filter { it.selected }
            if (selected.isEmpty()) {
                Toast.makeText(this, "선택된 항목이 없습니다", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val result = Intent()
            result.putStringArrayListExtra("names", ArrayList(selected.map { it.name }))
            result.putStringArrayListExtra("numbers", ArrayList(selected.map { it.number }))
            setResult(Activity.RESULT_OK, result)
            finish()
        }
    }

    private fun updateSelectedCount() {
        val count = allContacts.count { it.selected }
        tvSelectedCount.text = "${count}명 선택됨"
        btnDone.isEnabled = count > 0
        btnDone.alpha = if (count > 0) 1.0f else 0.5f
    }

    private fun updateSelectAllButton() {
        if (filtered.isEmpty()) {
            btnSelectAll.text = "전체 선택"
            return
        }
        val allSelected = filtered.all { it.selected }
        btnSelectAll.text = if (allSelected) "전체 해제" else "전체 선택"
    }

    private fun loadContacts() {
        val cursor: Cursor? = contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            ),
            null, null,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
        )
        val seen = mutableSetOf<String>()
        cursor?.use {
            while (it.moveToNext()) {
                val name = it.getString(0) ?: "이름없음"
                val number = it.getString(1) ?: ""
                val key = number.replace("-", "").replace(" ", "")
                if (number.isNotEmpty() && !seen.contains(key)) {
                    seen.add(key)
                    allContacts.add(PickerContact(name, number, false))
                }
            }
        }
    }
}

data class PickerContact(val name: String, val number: String, var selected: Boolean)

class PickerAdapter(
    private val items: List<PickerContact>,
    private val onChange: () -> Unit
) : RecyclerView.Adapter<PickerAdapter.VH>() {

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.tvName)
        val number: TextView = view.findViewById(R.id.tvNumber)
        val checkbox: CheckBox = view.findViewById(R.id.checkboxPick)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_picker, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val c = items[position]
        holder.name.text = c.name
        holder.number.text = c.number
        holder.checkbox.setOnCheckedChangeListener(null)
        holder.checkbox.isChecked = c.selected

        val toggle = {
            c.selected = !c.selected
            holder.checkbox.isChecked = c.selected
            onChange()
        }

        holder.itemView.setOnClickListener { toggle() }
        holder.checkbox.setOnCheckedChangeListener { _, isChecked ->
            if (c.selected != isChecked) {
                c.selected = isChecked
                onChange()
            }
        }
    }

    override fun getItemCount() = items.size
}
