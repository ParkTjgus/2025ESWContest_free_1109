package com.example.app.exercise

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.app.exercise.ExerciseItem
import com.example.app.R

class ExerciseAdapter(private val exerciseList: MutableList<ExerciseItem>) :
    RecyclerView.Adapter<ExerciseAdapter.ExerciseViewHolder>() {

    class ExerciseViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val exerciseTitle: TextView = itemView.findViewById(R.id.tv_exercise_title)
        val detailsLayout: LinearLayout = itemView.findViewById(R.id.details_layout)
        val setsTextView: TextView = itemView.findViewById(R.id.tv_sets)
        val repsTextView: TextView = itemView.findViewById(R.id.tv_reps)
        val roundTripTimeTextView: TextView = itemView.findViewById(R.id.tv_round_trip_time)
        val restTimeTextView: TextView = itemView.findViewById(R.id.tv_rest_time)
        val expandButton: ImageButton = itemView.findViewById(R.id.btn_expand)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ExerciseViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_exercise, parent, false)
        return ExerciseViewHolder(view)
    }

    override fun onBindViewHolder(holder: ExerciseViewHolder, position: Int) {
        val exercise = exerciseList[position]
        holder.exerciseTitle.text = exercise.name
        holder.setsTextView.text = "• 목표 세트: ${exercise.sets}"
        holder.repsTextView.text = "• 1세트당 목표 횟수: ${exercise.reps}"
        holder.roundTripTimeTextView.text = "• 1회 왕복 시간: ${exercise.roundTripTime}초"
        holder.restTimeTextView.text = "• 세트 간 쉬는 시간: ${exercise.restTime}초"

        // 확장/축소 로직
        var isExpanded = false
        holder.detailsLayout.visibility = View.GONE

        holder.expandButton.setOnClickListener {
            isExpanded = !isExpanded
            holder.detailsLayout.visibility = if (isExpanded) View.VISIBLE else View.GONE
            holder.expandButton.rotation = if (isExpanded) 180f else 0f
        }
    }

    override fun getItemCount(): Int {
        return exerciseList.size
    }
}