package com.lightdialer

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.lightdialer.data.ContactRow
import com.lightdialer.databinding.ItemContactBinding
import com.lightdialer.databinding.ItemSectionBinding
import com.lightdialer.util.Avatars

/**
 * Alphabetical contacts list: gray letter header + white card of rows,
 * each row a colored letter avatar + name (Samsung contacts look).
 */
class ContactsAdapter(
    private val onClick: (ContactRow) -> Unit,
    private val onLongClick: (ContactRow) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private sealed class Row {
        class Header(val title: String) : Row()
        class Item(val contact: ContactRow, val positionInCard: Int, val cardSize: Int) : Row()
    }

    private val rows = ArrayList<Row>()

    fun submit(list: List<ContactRow>) {
        rows.clear()
        var i = 0
        while (i < list.size) {
            val c = list[i]
            val section = sectionOf(c.name)
            rows.add(Row.Header(section))
            var j = i
            while (j < list.size && sectionOf(list[j].name) == section) j++
            val size = j - i
            for (k in 0 until size) {
                rows.add(Row.Item(list[i + k], k, size))
            }
            i = j
        }
        notifyDataSetChanged()
    }

    private fun sectionOf(name: String): String {
        val c = name.trim().firstOrNull() ?: return "#"
        return if (c.isLetterOrDigit()) c.uppercaseChar().toString() else "#"
    }

    class HeaderVH(val b: ItemSectionBinding) : RecyclerView.ViewHolder(b.root)
    class VH(val b: ItemContactBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inf = LayoutInflater.from(parent.context)
        return if (viewType == 0) {
            HeaderVH(ItemSectionBinding.inflate(inf, parent, false))
        } else {
            VH(ItemContactBinding.inflate(inf, parent, false))
        }
    }

    override fun getItemViewType(position: Int) = if (rows[position] is Row.Header) 0 else 1

    override fun getItemCount(): Int = rows.size

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val r = rows[position]) {
            is Row.Header -> (holder as HeaderVH).b.root.text = r.title
            is Row.Item -> {
                val b = (holder as VH).b
                val c = r.contact
                Avatars.bind(b.avatar, c.name)
                b.name.text = c.name
                b.root.setBackgroundResource(
                    when {
                        r.cardSize == 1 -> R.drawable.bg_row_single
                        r.positionInCard == 0 -> R.drawable.bg_row_top
                        r.positionInCard == r.cardSize - 1 -> R.drawable.bg_row_bottom
                        else -> R.drawable.bg_row_mid
                    }
                )
                b.root.setOnClickListener { onClick(c) }
                b.root.setOnLongClickListener {
                    onLongClick(c)
                    true
                }
            }
        }
    }

    fun contactAt(position: Int): ContactRow? = (rows.getOrNull(position) as? Row.Item)?.contact
}
