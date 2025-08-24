package com.example.app.exercise // 실제 패키지명으로 변경하세요

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.app.R // 실제 R 클래스 경로로 변경하세요
// ExerciseManager와 SessionState는 com.example.app.exercise 패키지에 있다고 가정합니다.
// ExerciseItem도 마찬가지로 import 필요

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
    private lateinit var lineChart: LineChart

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_exercise_set)

        Log.d("ExerciseSetActivity", "onCreate called. ExerciseManager state: ${ExerciseManager.state}")

        exerciseNameTextView = findViewById(R.id.exerciseNameTextView)
        currentRepsTextView = findViewById(R.id.currentRepsTextView)
        targetRepsTextView = findViewById(R.id.targetRepsTextView)
        setInfoTextView = findViewById(R.id.setInfoTextView)
        lineChart = findViewById(R.id.speedGraphView)
        skipButton = findViewById(R.id.skipButton)

        if (ExerciseManager.state == SessionState.IDLE) {
            Log.w("ExerciseSetActivity", "Exercise session not started (IDLE state on create). Finishing activity.")
            Toast.makeText(this, "운동 세션이 시작되지 않았습니다. 앱을 다시 시작해주세요.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        val currentExerciseOnCreate = ExerciseManager.getCurrentExercise()
        if (currentExerciseOnCreate == null) {
            Log.e("ExerciseSetActivity", "No current exercise data available (onCreate), but session not IDLE. State: ${ExerciseManager.state}. Finishing.")
            Toast.makeText(this, "현재 운동 정보를 가져올 수 없습니다. 앱을 다시 시작해주세요.", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        Log.d("ExerciseSetActivity", "onCreate: Initial exercise: ${currentExerciseOnCreate.name}, State: ${ExerciseManager.state}")


        // skipButton 로직 수정
        skipButton.setOnClickListener {
            val currentStateBeforeAction = ExerciseManager.state
            Log.d("ExerciseSetActivity", "Skip button clicked. Current state before action: $currentStateBeforeAction")

            if (currentStateBeforeAction == SessionState.WORKING) {
                ExerciseManager.moveToNextStep() // 상태가 RESTING 또는 FINISHED로 변경됨
                val newState = ExerciseManager.state
                Log.d("ExerciseSetActivity", "State after moveToNextStep(): $newState")

                if (newState == SessionState.RESTING) {
                    val currentExerciseForRest = ExerciseManager.getCurrentExercise()
                    if (currentExerciseForRest != null) {
                        Log.d("ExerciseSetActivity", "Transitioning to RestTimerActivity. Rest time: ${currentExerciseForRest.restTime}s")
                        val intent = Intent(this, RestTimerActivity::class.java)
                        // RestTimerActivity.EXTRA_REST_TIME_SECONDS는 RestTimerActivity에 정의된 상수여야 함
                        intent.putExtra("EXTRA_REST_TIME_SECONDS", currentExerciseForRest.restTime.toLong())
                        startActivity(intent)
                        // 중요: RestTimerActivity로 바로 넘어가므로 여기서는 refreshUi()를 호출하지 않음
                    } else {
                        // 이 경우는 발생하면 안 됨 (moveToNextStep이 RESTING으로 바꿨다면 다음 운동 또는 다음 세트가 있다는 의미)
                        Log.e("ExerciseSetActivity", "State is RESTING but currentExercise is null. This is unexpected. Refreshing UI.")
                        refreshUi()
                    }
                } else {
                    // newState가 FINISHED 이거나 다른 상태일 경우 (예: 에러로 IDLE이 된 경우)
                    // refreshUi()를 호출하여 해당 상태에 맞는 UI를 보여주거나 Activity를 종료함
                    Log.d("ExerciseSetActivity", "State is $newState (not RESTING after WORKING). Refreshing UI.")
                    refreshUi()
                }
            } else if (currentStateBeforeAction == SessionState.RESTING) {
                // ExerciseSetActivity의 '휴식 건너뛰기' 버튼은 RestTimerActivity에서 휴식을 관리하므로
                // 여기서는 토스트 메시지를 보여주거나, 이 버튼의 역할을 재정의해야 함.
                // 예를 들어, RestTimerActivity를 강제 종료하고 즉시 다음 운동 시작 ( ExerciseManager.finishRest() 호출 )
                Log.d("ExerciseSetActivity", "Skip button pressed during RESTING state in ExerciseSetActivity.")
                Toast.makeText(this, "휴식은 타이머 화면에서 관리됩니다.", Toast.LENGTH_SHORT).show()
                // 또는 ExerciseManager.finishRest() 호출 후 refreshUi() -> 이렇게 하면 타이머 없이 바로 다음 운동 시작 가능
                // ExerciseManager.finishRest()
                // refreshUi()
            } else {
                Log.d("ExerciseSetActivity", "Skip button clicked in unhandled state: $currentStateBeforeAction")
                refreshUi() // 예외 상황 처리
            }
        }
    }

    override fun onResume() {
        super.onResume()
        Log.d("ExerciseSetActivity", "onResume called. ExerciseManager state: ${ExerciseManager.state}")
        if (ExerciseManager.state == SessionState.IDLE) {
            Log.w("ExerciseSetActivity", "onResume: Session is IDLE (possibly after RestTimerActivity finished and all exercises done). Finishing activity.")
            Toast.makeText(this, "운동 세션이 종료되었거나 시작되지 않았습니다.", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        // RestTimerActivity에서 돌아왔을 때 (또는 처음 시작 시) UI를 올바르게 설정
        refreshUi()
    }

    private fun setupSpeedChartAppearance() {
        lineChart.clear()
        lineChart.description.isEnabled = false
        lineChart.setTouchEnabled(true)
        lineChart.isDragEnabled = true
        lineChart.setScaleEnabled(true)
        lineChart.setPinchZoom(true)

        val xAxis = lineChart.xAxis
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        xAxis.setDrawGridLines(true)
        xAxis.granularity = 1f

        val leftAxis = lineChart.axisLeft
        leftAxis.setDrawGridLines(true)

        lineChart.axisRight.isEnabled = false
        lineChart.legend.isEnabled = true
        Log.d("ExerciseSetActivity", "Chart appearance setup.")
    }

    private fun populateSpeedData() {
        val currentExercise = ExerciseManager.getCurrentExercise() ?: return
        val targetReps = currentExercise.reps

        val measuredEntries = ArrayList<Entry>()
        for (i in 0 until targetReps) {
            val speed = (2.0f + Math.random() * 1.5f).toFloat()
            measuredEntries.add(Entry(i.toFloat() + 1, speed))
        }
        val measuredDataSet = LineDataSet(measuredEntries, "측정된 속도 (예시)")
        measuredDataSet.color = Color.BLUE
        measuredDataSet.setCircleColor(Color.BLUE)
        measuredDataSet.lineWidth = 2f
        measuredDataSet.circleRadius = 3f
        measuredDataSet.setDrawValues(false)

        val targetEntries = ArrayList<Entry>()
        val targetSpeedValue = currentExercise.roundTripTime.toFloat()
        if (targetSpeedValue <= 0f) {
            Log.w("ExerciseSetActivity", "Target speed (roundTripTime) is invalid: $targetSpeedValue. Defaulting target line.")
        }

        for (i in 0 until targetReps) {
            targetEntries.add(Entry(i.toFloat() + 1, if (targetSpeedValue > 0) targetSpeedValue else 3.0f))
        }
        val targetDataSet = LineDataSet(targetEntries, "목표 속도 (시간)")
        targetDataSet.color = Color.RED
        targetDataSet.lineWidth = 1.5f
        targetDataSet.enableDashedLine(10f, 5f, 0f)
        targetDataSet.setDrawCircles(false)
        targetDataSet.setDrawValues(false)

        val dataSets = ArrayList<ILineDataSet>()
        dataSets.add(measuredDataSet)
        if (targetSpeedValue > 0) {
            dataSets.add(targetDataSet)
        }

        val lineData = LineData(dataSets)
        lineChart.data = lineData
        lineChart.invalidate()
        Log.d("ExerciseSetActivity", "Speed data populated. Target reps: $targetReps. Target speed value: $targetSpeedValue")
    }

    private fun refreshUi() {
        val currentExercise = ExerciseManager.getCurrentExercise() // onResume 등에서 호출될 때 getCurrentExercise 다시 호출
        val currentState = ExerciseManager.state

        Log.d("ExerciseSetActivity", "refreshUi. Current exercise: ${currentExercise?.name}, State: $currentState")

        // 뷰 가시성 초기화 (WORKING 상태에서 필요한 것들만 다시 VISIBLE로)
        currentRepsTextView.visibility = View.GONE
        targetRepsTextView.visibility = View.GONE
        lineChart.visibility = View.GONE

        if (currentState == SessionState.IDLE) {
            Log.w("ExerciseSetActivity", "refreshUi: Session is IDLE. Finishing activity.")
            Toast.makeText(this, "운동 세션이 종료되었거나 시작되지 않았습니다.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        if (currentState == SessionState.FINISHED) {
            Log.i("ExerciseSetActivity", "refreshUi: All exercises finished!")
            Toast.makeText(this, "모든 운동이 완료되었습니다!", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        if (currentExercise == null) {
            Log.e("ExerciseSetActivity", "refreshUi: currentExercise is null, but state is $currentState (not IDLE/FINISHED). This is unexpected. Finishing.")
            Toast.makeText(this, "운동 정보를 찾을 수 없습니다. 앱을 다시 시작해주세요.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        exerciseNameTextView.text = currentExercise.name

        when (currentState) {
            SessionState.WORKING -> {
                currentRepsTextView.visibility = View.VISIBLE
                targetRepsTextView.visibility = View.VISIBLE
                lineChart.visibility = View.VISIBLE

                setInfoTextView.text = ExerciseManager.getCurrentSetInfo()
                currentRepsTextView.text = "0" // TODO: 실제 반복 횟수 반영 필요
                targetRepsTextView.text = currentExercise.reps.toString()
                skipButton.text = "세트 완료"
                skipButton.isEnabled = true
                findViewById<View>(android.R.id.content).setBackgroundColor(Color.WHITE)
                lineChart.setBackgroundColor(Color.parseColor("#E0F7FA"))

                setupSpeedChartAppearance()
                populateSpeedData()
                Log.d("ExerciseSetActivity", "UI updated for WORKING state. Exercise: ${currentExercise.name}, Set: ${ExerciseManager.getCurrentSetInfo()}")
            }
            SessionState.RESTING -> {
                // 이 부분은 skipButton 클릭 시 RestTimerActivity로 바로 넘어가므로,
                // RestTimerActivity에서 돌아와 onResume -> refreshUi가 호출될 때까지는 실행되지 않음.
                // 만약 RestTimerActivity에서 돌아왔는데 여전히 RESTING 상태라면 (예: 사용자가 뒤로가기) 이 UI가 보임.
                setInfoTextView.text = "휴식 중..." // 또는 이전 운동 정보 표시
                skipButton.text = "휴식 건너뛰기" // 또는 다른 적절한 텍스트
                skipButton.isEnabled = true // 사용자가 원하면 여기서도 휴식 스킵 가능하게 할지 결정
                findViewById<View>(android.R.id.content).setBackgroundColor(Color.LTGRAY)
                lineChart.clear()
                lineChart.invalidate()
                lineChart.setBackgroundColor(Color.parseColor("#EEEEEE"))

                Log.d("ExerciseSetActivity", "UI updated for RESTING state (likely after returning from RestTimer or if skip logic changes).")
                // 일반적으로 RestTimerActivity로 인해 이 코드가 직접적으로 사용자에게 보이진 않음.
                // 하지만 만약 RestTimerActivity 없이 휴식을 직접 관리한다면 이 UI가 중요해짐.
                // 현재 로직에서는 RestTimerActivity를 사용하므로, 이 블록이 실행되는 경우는
                // RestTimerActivity에서 돌아왔는데, 어떤 이유로 ExerciseManager.finishRest()가 호출되지 않아
                // 여전히 RESTING 상태일 때 입니다. 이 경우를 대비해 UI를 설정합니다.
            }
            else -> {
                Log.w("ExerciseSetActivity", "refreshUi called with unexpected state: $currentState")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("ExerciseSetActivity", "onDestroy called. Current ExerciseManager state: ${ExerciseManager.state}")
    }
}
