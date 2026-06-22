package com.autoreply.app

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.json.JSONArray
import org.json.JSONObject

class TargetListActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var btnSelectAll: Button
    private lateinit var btnDelete: Button
    private lateinit var tvCount: TextView
    private lateinit var tvEmpty: TextView
    private lateinit var recycler: RecyclerView
    private lateinit var adapter: TargetAdapter

    private val items = mutableListOf<TargetItem>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_target_list)

        prefs = getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE)

        btnSelectAll = findViewById(R.id.btnSelectAll)
        btnDelete = findViewById(R.id.btnDelete)
        tvCount = findViewById(R.id.tvCount)
        tvEmpty = findViewById(R.id.tvEmpty)
        recycler = findViewById(R.id.recyclerTargets)

        loadItems()
        adapter = TargetAdapter(items) {
            updateUi()
        }
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter

        btnSelectAll.setOnClickListener {
            val allSelected = items.isNotEmpty() && items.all { it.selected }
            val newState = !allSelected
            for (item in items) item.selected = newState
            adapter.notifyDataSetChanged()
            updateUi()
        }

        btnDelete.setOnClickListener {
            val toDelete = items.filter { it.selected }
            if (toDelete.isEmpty()) {
                Toast.makeText(this, "선택된 항목이 없습니다", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            AlertDialog.Builder(this)
                .setTitle("삭제 확인")
                .setMessage("선택한 ${toDelete.size}명을 목록에서 제거할까요?")
                .setPositiveButton("삭제") { _, _ ->
                    items.removeAll(toDelete.toSet())
                    saveItems()
                    adapter.notifyDataSetChanged()
                    updateUi()
                    Toast.makeText(this, "${toDelete.size}명 삭제됨", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("취소", null)
                .show()
        }

        updateUi()
    }

    private fun updateUi() {
        val total = items.size
        val selectedCount = items.count { it.selected }
        tvCount.text = "총 ${total}명 · ${selectedCount}명 선택됨"

        if (total == 0) {
            tvEmpty.visibility = View.VISIBLE
            recycler.visibility = View.GONE
        } else {
            tvEmpty.visibility = View.GONE
            recycler.visibility = View.VISIBLE
        }

        btnDelete.isEnabled = selectedCount > 0
        btnDelete.alpha = if (selectedCount > 0) 1.0f else 0.4f

        val allSelected = total > 0 && items.all { it.selected }
        btnSelectAll.text = if (allSelected) "전체 해제" else "전체 선택"
        btnSelectAll.isEnabled = total > 0
        btnSelectAll.alpha = if (total > 0) 1.0f else 0.4f
    }

    private fun loadItems() {
        val json = prefs.getString(MainActivity.KEY_CONTACTS, "[]") ?: "[]"
        val arr = JSONArray(json)
        items.clear()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            items.add(TargetItem(obj.getString("name"), obj.getString("number"), false))
        }
    }

    private fun saveItems() {
        val arr = JSONArray()
        for (item in items) {
            val obj = JSONObject()
            obj.put("name", item.name)
            obj.put("number", item.number)
            arr.put(obj)
        }
        prefs.edit().putString(MainActivity.KEY_CONTACTS, arr.toString()).apply()
    }
}

data class TargetItem(val name: String, val number: String, var selected: Boolean)

class TargetAdapter(
    private val items: List<TargetItem>,
    private val onChange: () -> Unit
) : RecyclerView.Adapter<TargetAdapter.VH>() {

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.tvName)
        val number: TextView = view.findViewById(R.id.tvNumber)
        val checkbox: CheckBox = view.findViewById(R.id.checkboxTarget)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_target, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.name.text = item.name
        holder.number.text = item.number
        holder.checkbox.setOnCheckedChangeListener(null)
        holder.checkbox.isChecked = item.selected

        val toggle = {
            item.selected = !item.selected
            holder.checkbox.isChecked = item.selected
            onChange()
        }
        holder.itemView.setOnClickListener { toggle() }
        holder.checkbox.setOnCheckedChangeListener { _, isChecked ->
            if (item.selected != isChecked) {
                item.selected = isChecked
                onChange()
            }
        }
    }

    override fun getItemCount() = items.size
}
