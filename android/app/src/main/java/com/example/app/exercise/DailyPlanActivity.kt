package com.example.app

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
import com.example.app.exercise.ExerciseAdapter
import com.example.app.exercise.ExerciseItem
import com.example.app.exercise.ExerciseSetActivity
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*

class DailyPlanActivity : AppCompatActivity() {

    /*
    * UI 변수
    * */
    private lateinit var btnPrevDay: ImageButton
    private lateinit var btnNextDay: ImageButton
    private lateinit var tvDate: TextView
    private lateinit var rvExerciseList: RecyclerView
    private lateinit var btnAddExercise: Button
    private lateinit var btnStartWorkout: Button


    /**
     * DB
     */
    private lateinit var db: FirebaseFirestore

    private val exerciseList = mutableListOf<ExerciseItem>()
    private lateinit var exerciseAdapter: ExerciseAdapter
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
                Log.d("DailyPlanActivity", "First exercise in list: ${exerciseList[0].name}") // 첫 번째 운동 이름 로깅
                ExerciseManager.startExerciseSession(exerciseList)
                val intent = Intent(this, ExerciseSetActivity::class.java)
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

                    exerciseList.add(newExercise)
                    exerciseAdapter.notifyItemInserted(exerciseList.size - 1)

                    // ⭐ 운동 항목이 추가될 때마다 Firebase에 저장합니다.
                    saveWorkoutPlan()
                }
                dialog.dismiss()
            }
            .setNegativeButton("취소") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    // Firestore에서 운동 데이터를 로드하는 함수
    private fun loadExercisesForCurrentDate() {
        val dateKey = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(currentDate.time)

        db.collection("workout_plans")
            .document(dateKey)
            .get()
            .addOnSuccessListener { document ->
                exerciseList.clear()
                if (document.exists()) {
                    val exercises = document.get("exercises") as? List<Map<String, Any>>
                    exercises?.forEach { map ->
                        val name = map["name"] as? String ?: ""
                        val sets = (map["sets"] as? Long)?.toInt() ?: 0
                        val reps = (map["reps"] as? Long)?.toInt() ?: 0
                        val roundTripTime = (map["roundTripTime"] as? Long)?.toInt() ?: 0
                        val restTime = (map["restTime"] as? Long)?.toInt() ?: 0

                        val loadedExercise = ExerciseItem(name, sets, reps, roundTripTime, restTime)
                        exerciseList.add(loadedExercise)
                    }
                }
                exerciseAdapter.notifyDataSetChanged()
            }
            .addOnFailureListener {
                Toast.makeText(this, "데이터 로드 실패.", Toast.LENGTH_SHORT).show()
                exerciseList.clear()
                exerciseAdapter.notifyDataSetChanged()
            }
    }

    // Firestore에 운동 데이터를 저장하는 함수
    private fun saveWorkoutPlan() {
        val dateKey = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(currentDate.time)

        val exercisesToSave = exerciseList.map { exercise ->
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
                Toast.makeText(this, "운동 계획이 Firebase에 저장되었습니다.", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "저장 실패: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}