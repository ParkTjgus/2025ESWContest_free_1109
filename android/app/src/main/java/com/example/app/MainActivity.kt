package com.example.app

import android.content.Intent
import android.os.Bundle
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
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var btnPrevDay: ImageButton
    private lateinit var btnNextDay: ImageButton
    private lateinit var tvDate: TextView
    private lateinit var rvExerciseList: RecyclerView
    private lateinit var btnAddExercise: Button
    private lateinit var btnStartWorkout: Button
    private lateinit var btnSavePlan: Button

    // 날짜별 운동 계획을 저장하는 Map
    private val workoutPlans = mutableMapOf<String, MutableList<ExerciseItem>>()

    private val exerciseList = mutableListOf<ExerciseItem>()
    private lateinit var exerciseAdapter: ExerciseAdapter
    private var currentDate = Calendar.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 1. 모든 뷰를 먼저 초기화합니다.
        btnPrevDay = findViewById(R.id.btn_prev_day)
        btnNextDay = findViewById(R.id.btn_next_day)
        tvDate = findViewById(R.id.tv_date)
        rvExerciseList = findViewById(R.id.rv_exercise_list)
        btnAddExercise = findViewById(R.id.btn_add_exercise)
        btnStartWorkout = findViewById(R.id.btn_start_workout)

        // 뷰 초기화 후 버튼을 맨 앞으로 가져옵니다.
        btnPrevDay.bringToFront()
        btnNextDay.bringToFront()

        // 2. 뷰 초기화가 완료된 후, RecyclerView를 설정합니다.
        exerciseAdapter = ExerciseAdapter(exerciseList)
        rvExerciseList.layoutManager = LinearLayoutManager(this)
        rvExerciseList.adapter = exerciseAdapter

        // 3. 날짜 표시 초기화 및 리스너를 설정합니다.
        updateDateDisplay()
        loadExercisesForCurrentDate() // 시작 시 현재 날짜의 운동 목록 로드

        // 리스너 설정
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
            val intent = Intent(this, BleWaitingActivity::class.java)
            startActivity(intent)
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
                    val newExercise = ExerciseItem(exerciseName, sets, reps, roundTripTime, restTime)

                    // 현재 날짜의 운동 목록에 운동 항목을 추가합니다.
                    val dateKey = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(currentDate.time)
                    val exercises = workoutPlans.getOrPut(dateKey) { mutableListOf() }
                    exercises.add(newExercise)

                    // 화면에 표시되는 목록도 업데이트합니다.
                    exerciseList.add(newExercise)
                    exerciseAdapter.notifyItemInserted(exerciseList.size - 1)
                }
                dialog.dismiss()
            }
            .setNegativeButton("취소") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun loadExercisesForCurrentDate() {
        // 현재 날짜 키를 가져옵니다.
        val dateKey = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(currentDate.time)

        // 해당 날짜의 운동 목록을 가져옵니다. 없으면 빈 목록을 반환합니다.
        val exercises = workoutPlans[dateKey] ?: emptyList()

        // 현재 화면에 표시된 목록을 지우고 새 목록으로 채웁니다.
        exerciseList.clear()
        exerciseList.addAll(exercises)

        // RecyclerView를 새로고침하여 화면을 업데이트합니다.
        exerciseAdapter.notifyDataSetChanged()
    }
}
