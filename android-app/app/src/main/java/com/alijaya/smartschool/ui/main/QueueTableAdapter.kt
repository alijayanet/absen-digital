package com.alijaya.smartschool.ui.main

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.alijaya.smartschool.R
import com.alijaya.smartschool.data.model.QueueTable
import com.alijaya.smartschool.databinding.ItemQueueTableBinding

class QueueTableAdapter : RecyclerView.Adapter<QueueTableAdapter.ViewHolder>() {

    private val items = mutableListOf<QueueTable>()

    fun submitList(newItems: List<QueueTable>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemQueueTableBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position], position)
    }

    override fun getItemCount(): Int = items.size

    inner class ViewHolder(private val binding: ItemQueueTableBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(table: QueueTable, index: Int) {
            val tableTitle = if (!table.tableName.isNullOrBlank()) {
                table.tableName.uppercase()
            } else if (!table.teacherName.isNullOrBlank()) {
                "MEJA ${table.teacherName.uppercase()}"
            } else {
                "MEJA ${index + 1} - ${(table.className ?: "MENGAJI").uppercase()}"
            }
            binding.tvTableName.text = tableTitle
            binding.tvTeacherName.text = "Pengajar: ${table.teacherName ?: table.className ?: "Ustadz / Ustadzah"}"

            val current = table.current
            if (current != null) {
                binding.layoutCurrentServing.visibility = View.VISIBLE
                binding.tvEmptyCurrent.visibility = View.GONE
                binding.tvCurrentNumber.text = String.format("%02d", current.queueNumber)
                binding.tvCurrentStudentName.text = current.studentName ?: "-"
                binding.tvCurrentNis.text = if (!current.nis.isNullOrBlank()) "NIS: ${current.nis}" else ""
                binding.tvTableStatusBadge.text = "SEDANG MENGAJI"
                binding.tvTableStatusBadge.setBackgroundResource(R.drawable.bg_badge_success)
                binding.tvTableStatusBadge.setTextColor(Color.parseColor("#34D399"))
            } else {
                binding.layoutCurrentServing.visibility = View.GONE
                binding.tvEmptyCurrent.visibility = View.VISIBLE
                binding.tvEmptyCurrent.text = "Meja ini siap memanggil antrian santri"
                binding.tvTableStatusBadge.text = "SIAP"
                binding.tvTableStatusBadge.setBackgroundResource(R.drawable.bg_badge_warning)
                binding.tvTableStatusBadge.setTextColor(Color.parseColor("#FBBF24"))
            }

            val waiting = table.nextWaiting
            val waitingCount = table.stats?.waiting ?: (waiting?.size ?: 0)
            binding.tvWaitingCounter.text = "$waitingCount Santri"

            if (!waiting.isNullOrEmpty()) {
                val formatted = waiting.joinToString("   •   ") { item ->
                    "#${String.format("%02d", item.queueNumber)} (${item.studentName ?: "-"})"
                }
                binding.tvNextWaiting.text = formatted
                binding.tvNextWaiting.setTextColor(Color.parseColor("#E2E8F0"))
            } else {
                binding.tvNextWaiting.text = "Tidak ada antrian menunggu"
                binding.tvNextWaiting.setTextColor(Color.parseColor("#64748B"))
            }

            val stats = table.stats
            binding.tvStatWaiting.text = "${stats?.waiting ?: 0}"
            binding.tvStatCompleted.text = "${stats?.completed ?: 0}"
            binding.tvStatTotal.text = "${stats?.total ?: 0}"
        }
    }
}
