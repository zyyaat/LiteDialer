package com.lightdialer

import android.provider.CallLog
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.lightdialer.data.CallEntry
import com.lightdialer.databinding.ItemCallLogBinding
import com.lightdialer.util.Avatars
import com.lightdialer.util.Formats
import java.text.DateFormat
import java.util.Date

class CallLogAdapter(
    private val onClick: (CallEntry) -> Unit,
    private val onLongClick: (CallEntry) -> Unit
) : RecyclerView.Adapter<CallLogAdapter.VH>() {

    private val items = ArrayList<CallEntry>()
    private val timeFormat = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)

    fun submit(list: List<CallEntry>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    class VH(val b: ItemCallLogBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(ItemCallLogBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val ctx = holder.b.root.context
        val e = items[position]

        val title = e.name
            ?: if (e.number.isBlank()) ctx.getString(R.string.unknown_number) else Formats.number(e.number)
        holder.b.name.text = title

        Avatars.bind(holder.b.avatar, e.name ?: e.number.ifBlank { "?" })

        val missed = e.type == CallLog.Calls.MISSED_TYPE || e.type == CallLog.Calls.REJECTED_TYPE
        when (e.type) {
            CallLog.Calls.INCOMING_TYPE -> {
                holder.b.typeIcon.setImageResource(R.drawable.ic_call_in)
                holder.b.typeIcon.setColorFilter(ContextCompat.getColor(ctx, R.color.green))
            }
            CallLog.Calls.OUTGOING_TYPE -> {
                holder.b.typeIcon.setImageResource(R.drawable.ic_call_out)
                holder.b.typeIcon.setColorFilter(ContextCompat.getColor(ctx, R.color.accent))
            }
            else -> {
                holder.b.typeIcon.setImageResource(R.drawable.ic_call_missed)
                holder.b.typeIcon.setColorFilter(ContextCompat.getColor(ctx, R.color.red))
            }
        }
        holder.b.name.setTextColor(
            ContextCompat.getColor(ctx, if (missed) R.color.red else R.color.text_primary)
        )

        var time = timeFormat.format(Date(e.date))
        if (e.duration > 0 && !missed) time += "  ·  " + Formats.duration(e.duration)
        holder.b.time.text = time

        holder.b.root.setOnClickListener { onClick(e) }
        holder.b.root.setOnLongClickListener {
            onLongClick(e)
            true
        }
    }
}
