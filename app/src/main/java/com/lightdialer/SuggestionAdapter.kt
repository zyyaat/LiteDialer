package com.lightdialer

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.lightdialer.data.Suggestion
import com.lightdialer.databinding.ItemSuggestionBinding
import com.lightdialer.util.Avatars

class SuggestionAdapter(
    private val onClick: (Suggestion) -> Unit
) : RecyclerView.Adapter<SuggestionAdapter.VH>() {

    private val items = ArrayList<Suggestion>()

    fun submit(list: List<Suggestion>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    class VH(val b: ItemSuggestionBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(ItemSuggestionBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val s = items[position]
        Avatars.bind(holder.b.sAvatar, s.name)
        holder.b.sName.text = s.name
        holder.b.sNumber.text = s.number
        holder.b.root.setOnClickListener { onClick(s) }
    }
}
