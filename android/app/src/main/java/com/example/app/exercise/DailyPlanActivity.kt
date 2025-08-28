package com.example.app.exercise // 실제 패키지명으로 변경하세요

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.app.R // 실제 R 클래스 경로로 변경하세요
import com.example.app.ble.BleScanActivity
// ExerciseManager는 com.example.app.exercise 패키지에 있다고 가정합니다.
// ExerciseItem은 별도 파일(ExerciseItem.kt)에 정의되어 있고, 같은 패키지이거나 import 되었다고 가정합니다.

import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*

// ExerciseItem data class 정의는 여기서 제거 (별도 파일 ExerciseItem.kt에 있다고 가정)

class DailyPlanActivity : AppCompatActivity() {

    private lateinit var btnPrevDay: ImageButton
    private lateinit var btnNextDay: ImageButton
    private lateinit var tvDate: TextView
    private lateinit var rvExerciseList: RecyclerView
    private lateinit var btnAddExercise: Button
    private lateinit var btnStartWorkout: Button

    private lateinit var db: FirebaseFirestore

    private val exerciseList = mutableListOf<ExerciseItem>() // Activity의 로컬 리스트
    private lateinit var exerciseAdapter: ExerciseAdapter // ExerciseAdapter는 별도로 구현 필요
    private var currentDate = Calendar.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_daily_plan)

        db = FirebaseFirestore.getInstance()

        btnPrevDay = findViewById(R.id.btn_prev_day)
        btnNextDay = findViewById(R.id.btn_next_day)
        tvDate = findViewById(R.id.tv_date)
        rvExerciseList = findViewById(R.id.rv_exercise_list)
        btnAddExercise = findViewById(R.id.btn_add_exercise)
        btnStartWorkout = findViewById(R.id.btn_start_workout)

        btnPrevDay.bringToFront()
        btnNextDay.bringToFront()

        exerciseAdapter = ExerciseAdapter(exerciseList)
        rvExerciseList.layoutManager = LinearLayoutManager(this)
        rvExerciseList.adapter = exerciseAdapter

        updateDateDisplay()
        loadExercisesForCurrentDate()

        btnPrevDay.setOnClickListener {
            currentDate.add(Calendar.DAY_OF_YEAR, -1)
            updateDateDisplay()
            loadExercisesForCurrentDate()
        }
        btnNextDay.setOnClickListener {
            currentDate.add(Calendar.DAY_OF_YEAR, 1)
            updateDateDisplay()
            loadExercisesForCurrentDate()
        }

        btnAddExercise.setOnClickListener {
            showAddExerciseDialog()
        }

        btnStartWorkout.setOnClickListener {
            Log.d("DailyPlanActivity", "btnStartWorkout clicked. exerciseList size: ${exerciseList.size}")
            if (exerciseList.isNotEmpty()) {
                Log.d("DailyPlanActivity", "First exercise in list: ${exerciseList[0].name}")
                ExerciseManager.startExerciseSession(this.exerciseList)
                val intent = Intent(this@DailyPlanActivity, BleScanActivity::class.java)
                startActivity(intent)
            } else {
                Toast.makeText(this, "운동 목록이 비어있습니다.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateDateDisplay() {
        val year = currentDate.get(Calendar.YEAR)
        val month = currentDate.get(Calendar.MONTH) + 1
        val day = currentDate.get(Calendar.DAY_OF_MONTH)
        tvDate.text = "${year}년 ${month}월 ${day}일"
    }

    private fun showAddExerciseDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_exercise, null)
        val etExerciseName = dialogView.findViewById<EditText>(R.id.et_exercise_name)
        val etReps = dialogView.findViewById<EditText>(R.id.et_reps)
        val etSets = dialogView.findViewById<EditText>(R.id.et_sets)
        val etRoundTripTime = dialogView.findViewById<EditText>(R.id.et_round_trip_time)
        val etRestTime = dialogView.findViewById<EditText>(R.id.et_rest_time)

        AlertDialog.Builder(this)
            .setTitle("운동 추가")
            .setView(dialogView)
            .setPositiveButton("추가") { dialog, _ ->
                val exerciseName = etExerciseName.text.toString()
                val reps = etReps.text.toString().toIntOrNull() ?: 0
                val sets = etSets.text.toString().toIntOrNull() ?: 0
                val roundTripTime = etRoundTripTime.text.toString().toIntOrNull() ?: 0
                val restTime = etRestTime.text.toString().toIntOrNull() ?: 0

                if (exerciseName.isNotBlank() && reps > 0 && sets > 0) {
                    val newExercise =
                        ExerciseItem(exerciseName, sets, reps, roundTripTime, restTime)

                    this.exerciseList.add(newExercise)
                    exerciseAdapter.notifyItemInserted(this.exerciseList.size - 1)

                    saveWorkoutPlan()
                }
                dialog.dismiss()
            }
            .setNegativeButton("취소") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun loadExercisesForCurrentDate() {
        val dateKey = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(currentDate.time)
        Log.d("DailyPlanActivity", "Loading exercises for date: $dateKey")

        db.collection("workout_plans")
            .document(dateKey)
            .get()
            .addOnSuccessListener { document ->
                Log.d("DailyPlanActivity", "Firestore success for date: $dateKey. Document exists: ${document.exists()}")
                this.exerciseList.clear()
                if (document.exists()) {
                    val exercises = document.get("exercises") as? List<Map<String, Any>>
                    exercises?.forEach { map ->
                        val name = map["name"] as? String ?: ""
                        val sets = (map["sets"] as? Long)?.toInt() ?: 0
                        val reps = (map["reps"] as? Long)?.toInt() ?: 0
                        val roundTripTime = (map["roundTripTime"] as? Long)?.toInt() ?: 0
                        val restTime = (map["restTime"] as? Long)?.toInt() ?: 0

                        val loadedExercise = ExerciseItem(name, sets, reps, roundTripTime, restTime)
                        this.exerciseList.add(loadedExercise)
                    }
                }
                exerciseAdapter.notifyDataSetChanged()
                Log.d("DailyPlanActivity", "Local exerciseList updated. Size: ${this.exerciseList.size}")
            }
            .addOnFailureListener { e ->
                Log.e("DailyPlanActivity", "Error loading exercises for $dateKey", e)
                Toast.makeText(this, "데이터 로드 실패: ${e.message}", Toast.LENGTH_SHORT).show()
                this.exerciseList.clear()
                exerciseAdapter.notifyDataSetChanged()
            }
    }

    private fun saveWorkoutPlan() {
        val dateKey = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(currentDate.time)

        val exercisesToSave = this.exerciseList.map { exercise ->
            hashMapOf(
                "name" to exercise.name,
                "sets" to exercise.sets,
                "reps" to exercise.reps,
                "roundTripTime" to exercise.roundTripTime,
                "restTime" to exercise.restTime
            )
        }

        val workoutPlanData = hashMapOf(
            "date" to dateKey,
            "exercises" to exercisesToSave
        )

        db.collection("workout_plans")
            .document(dateKey)
            .set(workoutPlanData)
            .addOnSuccessListener {
                Log.d("DailyPlanActivity", "Workout plan saved for $dateKey")
                Toast.makeText(this, "운동 계획이 Firebase에 저장되었습니다.", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Log.e("DailyPlanActivity", "Error saving workout plan for $dateKey", e)
                Toast.makeText(this, "저장 실패: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}
