package com.lightdialer

import android.graphics.Typeface
import android.provider.CallLog
import android.text.SpannableString
import android.text.Spanned
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.lightdialer.data.CallEntry
import com.lightdialer.databinding.ItemCallLogBinding
import com.lightdialer.databinding.ItemSectionBinding
import com.lightdialer.util.Formats
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Samsung-style recents list: a small gray day header followed by a white
 * card holding that day's calls. Consecutive calls to the same number are
 * grouped ("(6)") and every row shows the blue SIM badge + time on the far
 * side. Rows swipe (handled by MainActivity) to place a call.
 */
class CallLogAdapter(
    private val context: android.content.Context,
    private val onClick: (CallEntry) -> Unit,
    private val onLongClick: (CallEntry) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private sealed class Row {
        class Header(val title: String) : Row()
        class Item(
            val entry: CallEntry,
            val count: Int,
            val sim: String?,
            val positionInCard: Int,
            val cardSize: Int
        ) : Row()
    }

    private val rows = ArrayList<Row>()
    private val timeFormat = DateFormat.getTimeInstance(DateFormat.SHORT)
    private val dayFormat = SimpleDateFormat("d MMMM", Locale.getDefault())

    fun submit(list: List<CallEntry>) {
        rows.clear()
        val cal = Calendar.getInstance()
        val today = Calendar.getInstance()
        val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
        var i = 0
        while (i < list.size) {
            val e = list[i]
            cal.timeInMillis = e.date
            val sameDay = today.get(Calendar.YEAR) == cal.get(Calendar.YEAR) &&
                today.get(Calendar.DAY_OF_YEAR) == cal.get(Calendar.DAY_OF_YEAR)
            val isYesterday = yesterday.get(Calendar.YEAR) == cal.get(Calendar.YEAR) &&
                yesterday.get(Calendar.DAY_OF_YEAR) == cal.get(Calendar.DAY_OF_YEAR)
            val title = when {
                sameDay -> context.getString(R.string.today)
                isYesterday -> context.getString(R.string.yesterday)
                else -> dayFormat.format(Date(e.date))
            }
            rows.add(Row.Header(title))
            // group consecutive calls to the same number on the same day
            var j = i
            while (j < list.size) {
                val n = list[j]
                val nCal = Calendar.getInstance().apply { timeInMillis = n.date }
                val sameNumber = n.number == e.number
                val sameDayKey =
                    cal.get(Calendar.YEAR) == nCal.get(Calendar.YEAR) &&
                        cal.get(Calendar.DAY_OF_YEAR) == nCal.get(Calendar.DAY_OF_YEAR)
                if (!sameNumber || !sameDayKey) break
                j++
            }
            val groupSize = j - i
            val sim = list[i].accountId?.let { acc ->
                acc.filter { it.isDigit() }.lastOrNull()?.toString()
            }
            for (k in 0 until groupSize) {
                rows.add(Row.Item(list[i + k], groupSize, sim, k, groupSize))
            }
            i = j
        }
        notifyDataSetChanged()
    }

    class HeaderVH(val b: ItemSectionBinding) : RecyclerView.ViewHolder(b.root)
    class VH(val b: ItemCallLogBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == 0) {
            HeaderVH(ItemSectionBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        } else {
            VH(ItemCallLogBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        }
    }

    override fun getItemViewType(position: Int) = if (rows[position] is Row.Header) 0 else 1

    override fun getItemCount(): Int = rows.size

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val r = rows[position]) {
            is Row.Header -> (holder as HeaderVH).b.root.text = r.title
            is Row.Item -> bindItem((holder as VH).b, r)
        }
    }

    private fun bindItem(b: ItemCallLogBinding, r: Row.Item) {
        val ctx = b.root.context
        val e = r.entry

        val title = e.name
            ?: if (e.number.isBlank()) ctx.getString(R.string.unknown_number) else Formats.number(e.number)
        b.name.text = if (r.count > 1) {
            val s = SpannableString("$title (${r.count})")
            s.setSpan(StyleSpan(Typeface.BOLD), title.length, s.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            s
        } else title

        val missed = e.type == CallLog.Calls.MISSED_TYPE || e.type == CallLog.Calls.REJECTED_TYPE
        when (e.type) {
            CallLog.Calls.INCOMING_TYPE -> {
                b.typeIcon.setImageResource(R.drawable.ic_call_in)
                b.typeIcon.setColorFilter(ContextCompat.getColor(ctx, R.color.text_secondary))
            }
            CallLog.Calls.OUTGOING_TYPE -> {
                b.typeIcon.setImageResource(R.drawable.ic_call_out)
                b.typeIcon.setColorFilter(ContextCompat.getColor(ctx, R.color.green))
            }
            else -> {
                b.typeIcon.setImageResource(R.drawable.ic_call_missed)
                b.typeIcon.setColorFilter(ContextCompat.getColor(ctx, R.color.red))
            }
        }
        b.name.setTextColor(ContextCompat.getColor(ctx, R.color.text_primary))

        b.badge.text = r.sim ?: "1"
        var time = timeFormat.format(Date(e.date))
        if (e.duration > 0 && !missed) time += " · " + Formats.duration(e.duration)
        b.time.text = time

        // white card look: round the first/last row of each day group
        b.root.setBackgroundResource(
            when {
                r.cardSize == 1 -> R.drawable.bg_row_single
                r.positionInCard == 0 -> R.drawable.bg_row_top
                r.positionInCard == r.cardSize - 1 -> R.drawable.bg_row_bottom
                else -> R.drawable.bg_row_mid
            }
        )

        b.root.setOnClickListener { onClick(e) }
        b.root.setOnLongClickListener {
            onLongClick(e)
            true
        }
    }

    fun entryAt(position: Int): CallEntry? = (rows.getOrNull(position) as? Row.Item)?.entry
}
