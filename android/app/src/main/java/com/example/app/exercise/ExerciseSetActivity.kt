package com.example.app.exercise

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.app.R
import com.example.app.ble.BleConnectionManager
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet
import org.json.JSONObject
import android.util.Log // Log는 필요시 계속 사용

class ExerciseSetActivity : AppCompatActivity(), BleConnectionManager.BleDataListener {

    private lateinit var exerciseNameTextView: TextView
    private lateinit var currentRepsTextView: TextView
    private lateinit var targetRepsTextView: TextView
    private lateinit var setInfoTextView: TextView // 세트 정보 표시용
    private lateinit var debugLogTextView: TextView // 디버그 로그 표시용
    private lateinit var skipButton: Button
    private lateinit var lineChart: LineChart

    private val realTimeEntries = ArrayList<Entry>()
    private lateinit var realTimeDataSet: LineDataSet

    private val debugStringBuilder = StringBuilder()

    companion object {
        private const val TAG = "ExerciseSetActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_exercise_set)
        Log.d(TAG, "onCreate CALLED")

        exerciseNameTextView = findViewById(R.id.exerciseNameTextView)
        currentRepsTextView = findViewById(R.id.currentRepsTextView)
        targetRepsTextView = findViewById(R.id.targetRepsTextView)
        setInfoTextView = findViewById(R.id.setInfoTextView) // 세트 정보용 TextView
        debugLogTextView = findViewById(R.id.debugLogTextView) // 디버그 로그용 TextView
        lineChart = findViewById(R.id.speedGraphView)
        skipButton = findViewById(R.id.skipButton)

        debugLogTextView.movementMethod = ScrollingMovementMethod() // 스크롤 가능하게
        appendToDebugLog("onCreate: State=${ExerciseManager.state}")

        initializeChartComponents()

        val currentExerciseName = ExerciseManager.getCurrentExercise()?.name ?: "N/A"
        appendToDebugLog("onCreate Initial: State=${ExerciseManager.state}, Ex=${currentExerciseName}")

        if (ExerciseManager.state == SessionState.IDLE) {
            appendToDebugLog("onCreate: IDLE state. Finishing.")
            Toast.makeText(this, "운동 세션이 시작되지 않았습니다.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        val currentExerciseOnCreate = ExerciseManager.getCurrentExercise()
        if (currentExerciseOnCreate == null) {
            appendToDebugLog("onCreate: No current exercise. Finishing.")
            Toast.makeText(this, "현재 운동 정보를 가져올 수 없습니다.", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        appendToDebugLog("onCreate: InitialEx=${currentExerciseOnCreate.name}, State=${ExerciseManager.state}")

        skipButton.setOnClickListener {
            appendToDebugLog("Skip button clicked. Current state before: ${ExerciseManager.state}")
            val currentStateBeforeAction = ExerciseManager.state
            if (currentStateBeforeAction == SessionState.WORKING) {
                ExerciseManager.moveToNextStep()
                appendToDebugLog("State after moveToNextStep(): ${ExerciseManager.state}")
                if (ExerciseManager.state == SessionState.RESTING) {
                    val currentExerciseForRest = ExerciseManager.getCurrentExercise()
                    if (currentExerciseForRest != null) {
                        appendToDebugLog("To RestTimer. Rest: ${currentExerciseForRest.restTime}s")
                        val intent = Intent(this, RestTimerActivity::class.java)
                        intent.putExtra("EXTRA_REST_TIME_SECONDS", currentExerciseForRest.restTime.toLong())
                        startActivity(intent)
                    } else {
                        appendToDebugLog("State RESTING but currentEx NULL. Refreshing.")
                        refreshUi()
                    }
                } else {
                    refreshUi()
                }
            } else if (currentStateBeforeAction == SessionState.RESTING) {
                appendToDebugLog("Skip during RESTING (in ExSetActivity).")
                Toast.makeText(this, "휴식은 타이머 화면에서 관리됩니다.", Toast.LENGTH_SHORT).show()
            } else {
                appendToDebugLog("Skip in unhandled state: $currentStateBeforeAction")
                refreshUi()
            }
        }
    }

    private fun appendToDebugLog(message: String) {
        val logWithMessage = "[${System.currentTimeMillis() % 10000}] $message" // 간단한 타임스탬프 추가
        debugStringBuilder.append("\n").append(logWithMessage)
        if (debugStringBuilder.length > 3000) { // 길이 제한 3000자로 늘림
            debugStringBuilder.delete(0, debugStringBuilder.length - 3000)
        }
        debugLogTextView.text = debugStringBuilder.toString()

        val layout = debugLogTextView.layout
        if (layout != null) {
            val scrollAmount = layout.getLineTop(debugLogTextView.lineCount) - debugLogTextView.height
            if (scrollAmount > 0) {
                debugLogTextView.scrollTo(0, scrollAmount)
            } else {
                debugLogTextView.scrollTo(0, 0)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume CALLED")
        appendToDebugLog("onResume: State BEFORE listener reg=${ExerciseManager.state}")
        BleConnectionManager.setBleDataListener(this)
        appendToDebugLog("onResume: Listener REGISTERED.")

        val currentExerciseName = ExerciseManager.getCurrentExercise()?.name ?: "N/A"
        appendToDebugLog("onResume AfterReg: State=${ExerciseManager.state}, Ex=${currentExerciseName}")

        if (ExerciseManager.state == SessionState.IDLE) {
            appendToDebugLog("onResume: IDLE state. Finishing.")
            finish()
            return
        }
        refreshUi()
    }

    override fun onPause() {
        super.onPause()
        Log.d(TAG, "onPause CALLED")
        appendToDebugLog("onPause CALLED.")
        BleConnectionManager.setBleDataListener(null)
        appendToDebugLog("onPause: Listener UNREGISTERED.")
    }

    private fun initializeChartComponents() {
        appendToDebugLog("initializeChartComponents CALLED.")
        realTimeDataSet = LineDataSet(realTimeEntries, "측정된 실제 속도")
        realTimeDataSet.color = Color.BLUE
        realTimeDataSet.setCircleColor(Color.BLUE)
        realTimeDataSet.lineWidth = 2f
        realTimeDataSet.circleRadius = 3f
        realTimeDataSet.setDrawValues(false)
        setupSpeedChartAppearance()
    }

    private fun setupSpeedChartAppearance() {
        appendToDebugLog("setupSpeedChartAppearance CALLED.")
        lineChart.description.isEnabled = false
        lineChart.setTouchEnabled(true)
        lineChart.isDragEnabled = true
        lineChart.setScaleEnabled(true)
        lineChart.setPinchZoom(true)
        val xAxis = lineChart.xAxis
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        xAxis.setDrawGridLines(true)
        xAxis.granularity = 1f
        lineChart.axisLeft.setDrawGridLines(true)
        lineChart.axisRight.isEnabled = false
        lineChart.legend.isEnabled = true
    }

    private fun setupChartDataAndDisplay() {
        appendToDebugLog("setupChartDataAndDisplay CALLED.")
        val currentExercise = ExerciseManager.getCurrentExercise() ?: run {
            appendToDebugLog("setupChart: currentExercise is NULL.")
            return
        }
        appendToDebugLog("setupChart: Clearing entries (size=${realTimeEntries.size})")
        realTimeEntries.clear()

        val targetEntries = ArrayList<Entry>()
        val targetReps = currentExercise.reps
        val targetSpeedValue = currentExercise.roundTripTime.toFloat()
        if (targetSpeedValue <= 0f) {
            appendToDebugLog("Target speed invalid: $targetSpeedValue. Defaulting.")
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
        dataSets.add(realTimeDataSet)
        if (targetSpeedValue > 0) {
            dataSets.add(targetDataSet)
        }
        val lineData = LineData(dataSets)
        lineChart.data = lineData
        lineChart.invalidate()
        appendToDebugLog("Chart setup: TargetReps=${currentExercise.reps}, TargetSpeed=${targetSpeedValue}")
    }

    private fun refreshUi() {
        val currentExercise = ExerciseManager.getCurrentExercise()
        val currentState = ExerciseManager.state
        appendToDebugLog("refreshUi: Ex=${currentExercise?.name ?: "N/A"}, St=$currentState")

        currentRepsTextView.visibility = View.GONE
        targetRepsTextView.visibility = View.GONE
        lineChart.visibility = View.GONE
        findViewById<View>(android.R.id.content).setBackgroundColor(Color.WHITE) // 기본 배경색

        if (currentState == SessionState.IDLE) {
            appendToDebugLog("refreshUi: IDLE. Finishing.")
            Toast.makeText(this, "운동 세션이 종료되었거나 다음 운동 준비 중입니다.", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        if (currentState == SessionState.FINISHED) {
            appendToDebugLog("refreshUi: FINISHED. Finishing.")
            Toast.makeText(this, "모든 운동이 완료되었습니다!", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        if (currentExercise == null) {
            appendToDebugLog("refreshUi: currentExercise NULL but state $currentState. Finishing.")
            Toast.makeText(this, "운동 정보를 찾을 수 없습니다.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        exerciseNameTextView.text = currentExercise.name

        when (currentState) {
            SessionState.WORKING -> {
                currentRepsTextView.visibility = View.VISIBLE
                targetRepsTextView.visibility = View.VISIBLE
                lineChart.visibility = View.VISIBLE

                setInfoTextView.text = ExerciseManager.getCurrentSetInfo() // 세트 정보는 setInfoTextView에
                currentRepsTextView.text = "0"
                targetRepsTextView.text = currentExercise.reps.toString()
                skipButton.text = "세트 완료"
                skipButton.isEnabled = true
                lineChart.setBackgroundColor(Color.parseColor("#E0F7FA"))
                setupChartDataAndDisplay()
                appendToDebugLog("UI for WORKING. Set: ${ExerciseManager.getCurrentSetInfo()}")
            }
            SessionState.RESTING -> {
                setInfoTextView.text = "휴식 중... (다음 운동: ${currentExercise.name})" // 휴식 정보는 setInfoTextView에
                skipButton.text = "휴식 건너뛰기"
                skipButton.isEnabled = true
                findViewById<View>(android.R.id.content).setBackgroundColor(Color.LTGRAY)
                lineChart.visibility = View.GONE
                appendToDebugLog("UI for RESTING.")
            }
            else -> {
                appendToDebugLog("refreshUi with unexpected state: $currentState")
            }
        }
    }

    override fun onDataReceived(data: String, packetSize: Int) { // <<< packetSize 파라미터 추가
        // 이제 packetSize도 함께 로그에 표시
        appendToDebugLog("DataRecv (Size:$packetSize): '$data' | St: ${ExerciseManager.state}")
        try {
            val jsonObject = JSONObject(data)
            val time = jsonObject.optDouble("time", -1.0).toFloat()
            val count = jsonObject.optInt("count", -1)

            appendToDebugLog("Parsed: t=$time, c=$count")

            if (count == -1 || time == -1.0f) {
                appendToDebugLog("InvalidData: c=$count, t=$time | From (Size:$packetSize): '$data'")
                return
            }

            runOnUiThread {
                currentRepsTextView.text = count.toString()
                if (ExerciseManager.state == SessionState.WORKING) {
                    val newEntry = Entry(count.toFloat(), time)
                    realTimeDataSet.addEntry(newEntry)
                    lineChart.data.notifyDataChanged()
                    lineChart.notifyDataSetChanged()
                    lineChart.invalidate()
                    val currentEx = ExerciseManager.getCurrentExercise()
                    if (currentEx != null && count > currentEx.reps) {
                        appendToDebugLog("Count ($count) > Target (${currentEx.reps})")
                    }
                } else {
                    appendToDebugLog("NoGraphUpdate (Size:$packetSize): ${ExerciseManager.state} | Reps: $count")
                }
            }
        } catch (e: Exception) {
            appendToDebugLog("ERROR (Size:$packetSize): ${e.message} | Data: '$data'")
            Log.e(TAG, "Error in onDataReceived (Size:$packetSize, Data:'$data')", e) // Logcat에도 상세 정보 남김
        }
    }

    // BleConnectionManager.BleDataListener 인터페이스 구현
    override fun onDebugMessage(message: String) {
        appendToDebugLog("BLE_MAN: $message") // BleConnectionManager에서 오는 디버그 메시지
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy CALLED. State=${ExerciseManager.state}")
        Toast.makeText(this, "onDestroy: State=${ExerciseManager.state}", Toast.LENGTH_SHORT).show()
    }
}
