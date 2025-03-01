package paho.mqtt.java.example

import java.util.ArrayList
import androidx.recyclerview.widget.RecyclerView
import android.view.ViewGroup
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView

class HistoryAdapter(private val history: ArrayList<String>) : RecyclerView.Adapter<HistoryAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.history_row, parent, false)
        return ViewHolder(view)
    }

    fun add(data: String) {
        history.add(data)
        notifyItemInserted(history.size - 1)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.mTextView.text = history[position]
    }

    override fun getItemCount() = history.size

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        var mTextView: TextView = view.findViewById(R.id.row_text)
    }
}