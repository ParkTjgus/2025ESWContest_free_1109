package com.example.app.exercise

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.app.R
import com.example.app.ExerciseManager
import com.example.app.SessionState
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet

class ExerciseSetActivity : AppCompatActivity() {

    private lateinit var exerciseNameTextView: TextView
    private lateinit var currentRepsTextView: TextView
    private lateinit var targetRepsTextView: TextView
    private lateinit var setInfoTextView: TextView
    private lateinit var skipButton: Button
    private lateinit var lineChart: LineChart // LineChart 멤버 변수

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_exercise_set)

        Log.d("ExerciseSetActivity", "onCreate called")

        exerciseNameTextView = findViewById(R.id.exerciseNameTextView)
        currentRepsTextView = findViewById(R.id.currentRepsTextView)
        targetRepsTextView = findViewById(R.id.targetRepsTextView)
        setInfoTextView = findViewById(R.id.setInfoTextView)
        lineChart = findViewById(R.id.speedGraphView) // LineChart 초기화 (speedGraphView ID 사용)
        skipButton = findViewById(R.id.skipButton)

        if (ExerciseManager.getCurrentExercise() == null && ExerciseManager.state != SessionState.IDLE) {
            Log.e("ExerciseSetActivity", "No exercise data, but state is not IDLE. State: ${ExerciseManager.state}. Finishing.")
            Toast.makeText(this, "시작할 운동 정보가 없습니다. 앱을 다시 시작해주세요.", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        if (ExerciseManager.state == SessionState.IDLE && ExerciseManager.getCurrentExercise() == null) {
             Log.w("ExerciseSetActivity", "Exercise session not started (IDLE state). Ensure session is started before launching ExerciseSetActivity.")
             Toast.makeText(this, "운동 세션이 시작되지 않았습니다.", Toast.LENGTH_SHORT).show()
             finish()
             return
        }

        skipButton.setOnClickListener {
            Log.d("ExerciseSetActivity", "Skip button clicked. Current state: ${ExerciseManager.state}")
            when (ExerciseManager.state) {
                SessionState.WORKING -> {
                    ExerciseManager.moveToNextStep()
                }
                SessionState.RESTING -> {
                    // ExerciseSetActivity에서는 휴식 중 스킵 버튼을 직접 처리하지 않음 (RestTimerActivity가 담당)
                    // 필요하다면 ExerciseManager.finishRest() 호출 고려 가능하나, RestTimerActivity에서 처리하는 것이 일반적
                    Log.d("ExerciseSetActivity", "Skip button pressed during RESTING state - RestTimerActivity should handle this.")
                }
                else -> {
                    Log.d("ExerciseSetActivity", "Skip button clicked in unhandled state: ${ExerciseManager.state}")
                }
            }
            refreshUi()
        }
        // refreshUi() 가 onCreate 마지막이나 onResume 에서 호출되므로 여기서 차트 설정 불필요
    }

    override fun onResume() {
        super.onResume()
        Log.d("ExerciseSetActivity", "onResume called. Refreshing UI.")
        refreshUi()
    }

    private fun setupSpeedChartAppearance() {
        lineChart.description.isEnabled = false
        lineChart.setTouchEnabled(true)
        lineChart.isDragEnabled = true
        lineChart.setScaleEnabled(true)
        lineChart.setPinchZoom(true)
        // lineChart.setBackgroundColor(Color.LTGRAY) // WORKING 상태 배경색은 여기서 설정하지 않음

        val xAxis = lineChart.xAxis
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        xAxis.setDrawGridLines(true)
        xAxis.granularity = 1f
        // xAxis.valueFormatter = ... // 필요시 X축 값 포맷터 설정 (e.g., "1회", "2회")

        val leftAxis = lineChart.axisLeft
        leftAxis.setDrawGridLines(true)
        // leftAxis.axisMinimum = 0f // 데이터에 따라 자동 조절되도록 하거나, 필요시 설정
        // leftAxis.axisMaximum = 5f

        lineChart.axisRight.isEnabled = false
        lineChart.legend.isEnabled = true
    }

    private fun populateSampleSpeedData() {
        val currentExercise = ExerciseManager.getCurrentExercise()
        val targetReps = currentExercise?.reps ?: 5 // 현재 운동 목표 횟수, 없으면 5회로 가정

        val measuredEntries = ArrayList<Entry>()
        for (i in 0 until targetReps) {
            val speed = (2.0f + Math.random() * 1.5f).toFloat() // 2.0 ~ 3.5 사이 랜덤 속도
            measuredEntries.add(Entry(i.toFloat(), speed))
        }
        val measuredDataSet = LineDataSet(measuredEntries, "측정된 속도")
        measuredDataSet.color = Color.BLUE
        measuredDataSet.setCircleColor(Color.BLUE)
        measuredDataSet.lineWidth = 2f
        measuredDataSet.circleRadius = 3f
        measuredDataSet.setDrawValues(false)

        val targetEntries = ArrayList<Entry>()
        val targetSpeedValue = currentExercise?.roundTripTime ?: 3.0f // ExerciseItem에 targetSpeed가 있다고 가정, 없으면 3.0f
        for (i in 0 until targetReps) {
            val measuredSpeedValue: Float = (2.0f + Math.random() * 1.5f).toFloat()
            measuredEntries.add(Entry(i.toFloat(), measuredSpeedValue))

            val finalTargetSpeedValue: Float = currentExercise?.roundTripTime?.toFloat() ?: 3.0f
            targetEntries.add(Entry(i.toFloat(), finalTargetSpeedValue))

        }
        val targetDataSet = LineDataSet(targetEntries, "목표 속도")
        targetDataSet.color = Color.RED
        targetDataSet.lineWidth = 1.5f
        targetDataSet.enableDashedLine(10f, 5f, 0f)
        targetDataSet.setDrawCircles(false)
        targetDataSet.setDrawValues(false)

        val dataSets = ArrayList<ILineDataSet>()
        dataSets.add(measuredDataSet)
        dataSets.add(targetDataSet)

        val lineData = LineData(dataSets)
        lineChart.data = lineData
        lineChart.invalidate()
        Log.d("ExerciseSetActivity", "Sample speed data populated for $targetReps reps. Target speed: $targetSpeedValue")
    }

    private fun refreshUi() {
        val currentExercise = ExerciseManager.getCurrentExercise()
        val currentState = ExerciseManager.state

        Log.d("ExerciseSetActivity", "refreshUi. Current exercise: ${currentExercise?.name}, State: $currentState")

        if (currentExercise == null || currentState == SessionState.FINISHED) {
            if (currentState == SessionState.FINISHED) {
                Log.i("ExerciseSetActivity", "All exercises finished!")
                Toast.makeText(this, "모든 운동이 완료되었습니다!", Toast.LENGTH_SHORT).show()
            } else {
                Log.e("ExerciseSetActivity", "No exercise data. Current exercise is null: ${currentExercise == null}, State: $currentState. Finishing activity.")
                Toast.makeText(this, "운동 정보가 없거나 세션이 종료되었습니다.", Toast.LENGTH_SHORT).show()
            }
            finish()
            return
        }

        exerciseNameTextView.text = currentExercise.name
        targetRepsTextView.text = currentExercise.reps.toString()

        // setInfoTextView 내용을 상태에 따라 분기하여 설정
        if (currentState == SessionState.RESTING) {
            setInfoTextView.text = "휴식 시간"
        } else {
            setInfoTextView.text = ExerciseManager.getCurrentSetInfo()
        }

        when (currentState) {
            SessionState.WORKING -> {
                currentRepsTextView.text = "0" // 실제 운동 중 업데이트 필요
                skipButton.text = "세트 완료"
                skipButton.isEnabled = true
                lineChart.setBackgroundColor(Color.parseColor("#E0F7FA")) // 연한 파란색 배경

                setupSpeedChartAppearance()
                populateSampleSpeedData()
                Log.d("ExerciseSetActivity", "UI updated for WORKING state. Exercise: ${currentExercise.name}")
            }
            SessionState.RESTING -> {
                // setInfoTextView는 이미 위에서 "휴식 시간"으로 설정됨
                skipButton.isEnabled = false
                lineChart.data = null
                lineChart.invalidate()
                lineChart.setBackgroundColor(Color.parseColor("#EEEEEE"))

                Log.d("ExerciseSetActivity", "Starting RestTimerActivity. Rest time: ${currentExercise.restTime} seconds")
                val intent = Intent(this, RestTimerActivity::class.java)
                intent.putExtra(RestTimerActivity.EXTRA_REST_TIME_SECONDS, currentExercise.restTime.toLong())
                startActivity(intent)
            }
            SessionState.IDLE -> {
                Log.w("ExerciseSetActivity", "refreshUi called in IDLE state. This might indicate an issue. Finishing activity.")
                Toast.makeText(this, "운동 세션이 시작되지 않았습니다.", Toast.LENGTH_SHORT).show()
                finish()
            }
            SessionState.FINISHED -> {
                Log.d("ExerciseSetActivity", "refreshUi called in FINISHED state, though it should have been handled earlier in the function.")
            }
        }
    }


    override fun onDestroy() {
        super.onDestroy()
        Log.d("ExerciseSetActivity", "onDestroy called")
    }
}
// ExerciseItem에 targetSpeed 필드가 있다고 가정하고 populateSampleSpeedData에서 사용함.
// 예시: data class ExerciseItem(..., val targetSpeed: Float = 3.0f)
