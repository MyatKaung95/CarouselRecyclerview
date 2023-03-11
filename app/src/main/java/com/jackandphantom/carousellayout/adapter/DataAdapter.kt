package com.jackandphantom.carousellayout.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.customviewimple.model.DataModel
import com.jackandphantom.carousellayout.R

class DataAdapter(private var list: ArrayList<DataModel>) :
    RecyclerView.Adapter<DataAdapter.ViewHolder>() {

    private var selectedPositions = HashSet<Int>()
    private var onItemSelectedListener: OnItemSelectedListener? = null

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val image: ImageView = itemView.findViewById(R.id.image)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val inflater = LayoutInflater.from(parent.context).inflate(R.layout.item_view, parent, false)
        return ViewHolder(inflater)
    }

    override fun getItemCount(): Int {
        return list.size
    }

    override fun getItemViewType(position: Int): Int {
        return list[position].id
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        // Set the scale and translation if the item is selected
        if (selectedPositions.contains(position)) {
            holder.itemView.scaleX = 1.2f
            holder.itemView.scaleY = 1.2f
            //holder.itemView.translationY = -20.0f
        } else {
            holder.itemView.scaleX = 1.0f
            holder.itemView.scaleY = 1.0f
            //holder.itemView.translationY = 0.0f
        }

        Glide.with(holder.image).load(list[position].imageResourceId).into(holder.image)
        holder.itemView.setOnClickListener {
            // Check if the maximum number of selected items has been reached
            if (selectedPositions.size == 6 && !selectedPositions.contains(position)) {
                // Show a toast message or do something to alert the user
                return@setOnClickListener
            }

            // Toggle the selected position and notify the adapter of the change
            if (selectedPositions.contains(position)) {
                selectedPositions.remove(position)
            } else {
                selectedPositions.add(position)
            }
            notifyItemChanged(position)

            // Call the listener and pass the selected positions
            onItemSelectedListener?.onItemSelected(selectedPositions.toList())
        }
    }


    fun updateData(list: ArrayList<DataModel>) {
        this.list = list
        notifyDataSetChanged()
    }

    // Use the method for item changed
    fun itemChanged() {
        // update the first item for test purposes
        this.list[0] = DataModel(0, R.drawable.tarot, "This is cool")
        notifyItemChanged(0)
    }

    // Use the method for checking the itemRemoved
    fun removeData() {
        // remove the last item for test purposes
        val orgListSize = list.size
        this.list = ArrayList(this.list.subList(0, orgListSize - 1))
        notifyItemRemoved(orgListSize - 1)
    }

    // Add interface and listener for item selection
    interface OnItemSelectedListener {
        fun onItemSelected(selectedPositions: List<Int>)
    }

    fun setOnItemSelectedListener(onItemSelectedListener: OnItemSelectedListener) {
        this.onItemSelectedListener = onItemSelectedListener
    }

    // Add a method to get the selected positions
    fun getSelectedPositions(): Set<Int> {
        return selectedPositions
    }
}




