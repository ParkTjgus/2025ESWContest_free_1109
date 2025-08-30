package com.example.app.exercise

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.app.BleWaitingActivity // "START REQ 받는 곳"으로 가정
import com.example.app.R
import com.example.app.ble.BleConnectionManager
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet
import org.json.JSONObject

class ExerciseSetActivity : AppCompatActivity(), BleConnectionManager.BleDataListener {

    private lateinit var exerciseNameTextView: TextView
    private lateinit var currentRepsTextView: TextView
    private lateinit var targetRepsTextView: TextView
    private lateinit var setInfoTextView: TextView
    private lateinit var debugLogTextView: TextView
    private lateinit var skipButton: Button
    private lateinit var lineChart: LineChart

    private val realTimeEntries = ArrayList<Entry>()
    private lateinit var realTimeDataSet: LineDataSet

    private val debugStringBuilder = StringBuilder()

    private var internalRepsCount = 0 // 앱 자체 횟수 카운터

    // 중복 데이터 필터링을 위한 변수
    private var lastReceivedTime: Float = -10.0f
    private var lastReceivedDeviceCount: Int = -10
    private var lastReceiveTimestamp: Long = 0L

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
        setInfoTextView = findViewById(R.id.setInfoTextView)
        debugLogTextView = findViewById(R.id.debugLogTextView)
        lineChart = findViewById(R.id.speedGraphView)
        skipButton = findViewById(R.id.skipButton)

        debugLogTextView.movementMethod = ScrollingMovementMethod()
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
                val nextState = ExerciseManager.state
                val nextExerciseForRest = ExerciseManager.getCurrentExercise()

                if (nextState == SessionState.RESTING) {
                    if (nextExerciseForRest != null) {
                        appendToDebugLog("To RestTimer. Rest: ${nextExerciseForRest.restTime}s for ${nextExerciseForRest.name}")
                        val intent = Intent(this, RestTimerActivity::class.java)
                        intent.putExtra("EXTRA_REST_TIME_SECONDS", nextExerciseForRest.restTime.toLong())
                        startActivity(intent)
                    } else {
                        appendToDebugLog("State RESTING but currentEx NULL. Refreshing.")
                        refreshUi() // 예외 상황 처리, 보통은 FINISHED나 IDLE로 갈 수 있음
                    }
                } else if (nextState == SessionState.FINISHED) {
                    appendToDebugLog("Manually skipped or auto-advanced to FINISHED state.")
                    Toast.makeText(this, "모든 운동이 완료되었습니다!", Toast.LENGTH_LONG).show()
                    val intent = Intent(this, BleWaitingActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    startActivity(intent)
                    finish()
                } else { // WORKING (다음 세트/운동), IDLE (예외적 종료)
                    refreshUi()
                }
            } else if (currentStateBeforeAction == SessionState.RESTING) {
                ExerciseManager.moveToNextStep() // 휴식 건너뛰고 다음 운동으로
                refreshUi()
                appendToDebugLog("Skip during RESTING (in ExSetActivity). Moved to next. New state: ${ExerciseManager.state}")
            } else { // IDLE, FINISHED 등 다른 상태에서 스킵 (일반적이지 않음)
                appendToDebugLog("Skip in unhandled state: $currentStateBeforeAction")
                refreshUi() // UI 갱신 (IDLE이면 종료될 수 있음)
            }
        }
    }

    private fun appendToDebugLog(message: String) {
        val logWithMessage = "[${System.currentTimeMillis() % 10000}] $message"
        debugStringBuilder.append("\n").append(logWithMessage)
        if (debugStringBuilder.length > 3000) {
            debugStringBuilder.delete(0, debugStringBuilder.length - 3000)
        }
        debugLogTextView.text = debugStringBuilder.toString()
        val layout = debugLogTextView.layout
        if (layout != null) {
            val scrollAmount = layout.getLineTop(debugLogTextView.lineCount) - debugLogTextView.height
            debugLogTextView.scrollTo(0, if (scrollAmount > 0) scrollAmount else 0)
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
            finish() // IDLE 상태면 액티비티 종료
            return
        }
        refreshUi() // 현재 상태에 맞게 UI 갱신
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
        xAxis.axisMinimum = 1f // X축은 1부터 시작
        lineChart.axisLeft.setDrawGridLines(true)
        lineChart.axisRight.isEnabled = false
        lineChart.legend.isEnabled = true
    }

    private fun setupChartDataAndDisplay() {
        appendToDebugLog("setupChartDataAndDisplay CALLED. Current internalRepsCount before clear: $internalRepsCount")
        val currentExercise = ExerciseManager.getCurrentExercise() ?: run {
            appendToDebugLog("setupChart: currentExercise is NULL. Cannot setup chart.")
            realTimeEntries.clear()
            lineChart.data = null // 차트 데이터 초기화
            lineChart.invalidate()
            return
        }
        appendToDebugLog("setupChart: Clearing realTimeEntries (size before clear=${realTimeEntries.size}) for ${currentExercise.name}")
        realTimeEntries.clear() // 새 운동/세트 시작 시 실시간 데이터 지우기

        val targetEntries = ArrayList<Entry>()
        val targetReps = currentExercise.reps
        val targetSpeedValue = currentExercise.roundTripTime.toFloat()

        if (targetSpeedValue <= 0f) {
            appendToDebugLog("Target speed invalid or not set: $targetSpeedValue. Target speed line will not be shown.")
        }

        for (i in 0 until targetReps) {
            targetEntries.add(Entry(i.toFloat() + 1, if (targetSpeedValue > 0) targetSpeedValue else 3.0f)) // X축은 1부터
        }
        val targetDataSet = LineDataSet(targetEntries, "목표 속도 (시간)")
        targetDataSet.color = Color.RED
        targetDataSet.lineWidth = 1.5f
        targetDataSet.enableDashedLine(10f, 5f, 0f)
        targetDataSet.setDrawCircles(false)
        targetDataSet.setDrawValues(false)

        val dataSets = ArrayList<ILineDataSet>()
        dataSets.add(realTimeDataSet) // 비어있는 실시간 데이터셋 추가
        if (targetSpeedValue > 0 && targetEntries.isNotEmpty()) {
            dataSets.add(targetDataSet) // 목표 속도 데이터셋 추가
        }

        val lineData = LineData(dataSets)
        lineChart.data = lineData
        lineChart.invalidate()
        appendToDebugLog("Chart setup complete for ${currentExercise.name}: TargetReps=${targetReps}, TargetSpeed=${targetSpeedValue}, RealTimeEntries initially: ${realTimeEntries.size}")
    }

    private fun refreshUi() {
        val currentExercise = ExerciseManager.getCurrentExercise()
        val currentState = ExerciseManager.state
        appendToDebugLog("refreshUi: Ex=${currentExercise?.name ?: "N/A"}, St=$currentState, InternalReps (before potential reset)=$internalRepsCount")

        // UI 요소 기본 상태 설정
        currentRepsTextView.visibility = View.GONE
        targetRepsTextView.visibility = View.GONE
        lineChart.visibility = View.GONE
        findViewById<View>(android.R.id.content).setBackgroundColor(Color.WHITE)

        if (currentState == SessionState.IDLE) {
            appendToDebugLog("refreshUi: IDLE. Finishing.")
            Toast.makeText(this, "운동 세션이 종료되었거나 다음 운동 준비 중입니다.", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        if (currentState == SessionState.FINISHED) {
            appendToDebugLog("refreshUi: FINISHED. All exercises complete. (This might be called if skipButton leads to FINISHED directly)")
            Toast.makeText(this, "모든 운동이 완료되었습니다!", Toast.LENGTH_LONG).show()
            // FINISHED 상태로의 전환은 skipButton.OnClickListener에서 BleWaitingActivity로 이동 처리하므로,
            // refreshUi가 FINISHED 상태로 호출되면 중복 처리가 될 수 있으나, 안전하게 finish()만 호출
            finish()
            return
        }
        if (currentExercise == null) {
            // IDLE, FINISHED가 아닌데 운동 정보가 없는 예외적인 경우
            appendToDebugLog("refreshUi: currentExercise NULL but state $currentState. Finishing.")
            Toast.makeText(this, "운동 정보를 찾을 수 없습니다. 세션을 종료합니다.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        exerciseNameTextView.text = currentExercise.name

        when (currentState) {
            SessionState.WORKING -> {
                internalRepsCount = 0 // 새 운동/세트 시작 시 내부 횟수 카운터 초기화
                appendToDebugLog("refreshUi: WORKING state. internalRepsCount reset to 0.")

                currentRepsTextView.visibility = View.VISIBLE
                targetRepsTextView.visibility = View.VISIBLE
                lineChart.visibility = View.VISIBLE

                setInfoTextView.text = ExerciseManager.getCurrentSetInfo()
                currentRepsTextView.text = internalRepsCount.toString() // 초기값 "0" 표시
                targetRepsTextView.text = currentExercise.reps.toString()
                skipButton.text = "세트 완료"
                skipButton.isEnabled = true // 운동 중에는 버튼 활성화
                lineChart.setBackgroundColor(Color.parseColor("#E0F7FA")) // 운동 중 배경색

                setupChartDataAndDisplay() // 차트 데이터 준비 및 표시
                appendToDebugLog("UI for WORKING. Set: ${ExerciseManager.getCurrentSetInfo()}, Target Reps: ${currentExercise.reps}")
            }
            SessionState.RESTING -> {
                // 휴식 상태는 RestTimerActivity에서 처리되므로, ExerciseSetActivity의 refreshUi가
                // RESTING 상태로 호출되는 경우는 RestTimerActivity에서 돌아온 직후임.
                // 이 경우 다음 운동을 준비해야 하므로 WORKING 상태로 UI를 갱신하거나,
                // ExerciseManager가 이미 다음 운동의 WORKING 상태로 변경했을 수 있음.
                // 만약 ExerciseManager.state가 RESTING으로 유지된다면, 휴식 UI를 보여줄 수 있음.
                // 하지만 현재 로직은 RestTimerActivity 종료 후 onResume -> refreshUi 호출 시
                // ExerciseManager.state가 다음 WORKING 또는 FINISHED/IDLE로 변경되어 있을 것을 기대함.
                // 명확성을 위해 휴식 UI 표시 로직은 skipButton 클릭 시 RestTimerActivity 호출 전으로 집중.
                // 여기서는 다음 운동을 준비하는 로직으로 바로 넘어가는 것이 일반적 (ExerciseManager가 상태를 변경했다면).
                appendToDebugLog("refreshUi: State is RESTING. This should ideally transition to WORKING for next set/exercise or FINISHED.")
                // 만약 RestTimerActivity에서 돌아왔는데 여전히 RESTING이면, 다음 운동으로 넘어가도록 시도
                ExerciseManager.moveToNextStep() // 다음 단계 (WORKING 또는 FINISHED)로 상태 전환 시도
                val newActualState = ExerciseManager.state
                appendToDebugLog("refreshUi (during RESTING): Attempted moveToNextStep. New actual state: $newActualState")
                if (newActualState == SessionState.WORKING || newActualState == SessionState.FINISHED || newActualState == SessionState.IDLE) {
                    refreshUi() // 바뀐 상태에 따라 UI 다시 갱신 (재귀호출이지만 상태가 바뀌었으므로 괜찮음)
                } else {
                    // 여전히 RESTING이거나 예외적인 경우.
                    setInfoTextView.text = "휴식 완료 후 다음 운동 준비 중..."
                    skipButton.text = "다음 운동 시작" // 수동으로 다음 운동 시작하도록 유도
                    skipButton.isEnabled = true
                    findViewById<View>(android.R.id.content).setBackgroundColor(Color.LTGRAY)
                    lineChart.visibility = View.GONE
                }
            }
            else -> { // 기타 상태
                appendToDebugLog("refreshUi with unexpected state: $currentState for exercise ${currentExercise.name}")
            }
        }
    }

    override fun onDataReceived(data: String, packetSize: Int) {
        val rawCountFromDevice: Int
        val time: Float
        val currentTimestamp = System.currentTimeMillis()

        appendToDebugLog("DataRecv (Size:$packetSize): '$data' | St: ${ExerciseManager.state}, InternalReps (before inc): $internalRepsCount")

        try {
            val jsonObject = JSONObject(data)
            time = jsonObject.optDouble("time", -1.0).toFloat()
            rawCountFromDevice = jsonObject.optInt("count", -1) // 아두이노가 보낸 count 값

            appendToDebugLog("Parsed: t=$time, device_c=$rawCountFromDevice")

            if (time == -1.0f) { // 유효하지 않은 time 값은 무시
                appendToDebugLog("InvalidData: time is -1.0. Ignoring.")
                return
            }

            // 중복 데이터 필터링 로직
            if (time == lastReceivedTime && rawCountFromDevice == lastReceivedDeviceCount) {
                if ((currentTimestamp - lastReceiveTimestamp) < 500) { // 0.5초 이내 동일 데이터는 중복으로 간주
                    appendToDebugLog("Duplicate data filtered: t=$time, dev_c=$rawCountFromDevice, dT=${currentTimestamp - lastReceiveTimestamp}ms. Ignoring this packet.")
                    return // 이 데이터 처리를 건너뜀
                }
            }
            // 중복이 아니거나, 중복이라도 시간 간격이 충분히 있다면, 마지막 수신 정보 업데이트
            lastReceivedTime = time
            lastReceivedDeviceCount = rawCountFromDevice
            lastReceiveTimestamp = currentTimestamp
            // 중복 데이터 필터링 로직 끝

            runOnUiThread {
                if (ExerciseManager.state == SessionState.WORKING) {
                    internalRepsCount++ // 앱 내부 카운터 증가
                    currentRepsTextView.text = internalRepsCount.toString() // UI에 내부 카운터 표시

                    appendToDebugLog("WORKING: internalRepsCount incremented to $internalRepsCount. time=$time")

                    val newEntry = Entry(internalRepsCount.toFloat(), time) // 내부 카운터를 X축으로 사용
                    appendToDebugLog("Adding to Chart: X(InternalRep)=${internalRepsCount.toFloat()}, Y(Time)=$time, DataSetSizeBeforeAdd=${realTimeDataSet.entryCount}")

                    realTimeDataSet.addEntry(newEntry)
                    appendToDebugLog("DataSetSizeAfterAdd=${realTimeDataSet.entryCount}")

                    lineChart.data.notifyDataChanged()
                    lineChart.notifyDataSetChanged()
                    lineChart.invalidate()

                    val currentEx = ExerciseManager.getCurrentExercise()
                    if (currentEx != null && internalRepsCount >= currentEx.reps) {
                        // 목표 횟수 도달 시 "세트 완료" 버튼 자동 클릭
                        appendToDebugLog("Target reps reached ($internalRepsCount/${currentEx.reps}) for ${currentEx.name}. Auto-clicking 'Set Complete' button.")

                        if (skipButton.isEnabled) {
                            skipButton.performClick() // skipButton의 OnClickListener에 구현된 로직 실행
                        } else {
                            // 버튼이 비활성화된 예외적인 경우 (예: 상태 전환 중이거나 오류)
                            appendToDebugLog("WARN: Target reps reached, but skipButton is not enabled. Manual skip might be needed or check state logic. Attempting direct advance.")
                            // 이 경우, 이전처럼 직접 상태를 전환하고 UI를 갱신하는 로직을 실행할 수 있으나,
                            // skipButton의 onClickListener가 주된 로직이므로, 이 상황은 디버깅 필요.
                            // 임시로 직접 진행 로직을 호출 (skipButton.onClick 로직과 유사하게)
                            ExerciseManager.moveToNextStep()
                            val nextState = ExerciseManager.state
                            val nextExerciseForRest = ExerciseManager.getCurrentExercise()
                            if (nextState == SessionState.RESTING && nextExerciseForRest != null) {
                                val intent = Intent(this, RestTimerActivity::class.java)
                                intent.putExtra("EXTRA_REST_TIME_SECONDS", nextExerciseForRest.restTime.toLong())
                                startActivity(intent)
                            } else if (nextState == SessionState.FINISHED) {
                                Toast.makeText(this, "모든 운동이 완료되었습니다!", Toast.LENGTH_LONG).show()
                                val intent = Intent(this, BleWaitingActivity::class.java)
                                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                                startActivity(intent)
                                finish()
                            } else {
                                refreshUi()
                            }
                        }
                    }
                } else {
                    appendToDebugLog("NoGraphUpdate (Not WORKING state): ${ExerciseManager.state} | InternalReps: $internalRepsCount, Time: $time, DeviceCount: $rawCountFromDevice")
                }
            }
        } catch (e: Exception) {
            appendToDebugLog("ERROR (Size:$packetSize): ${e.message} | Data: '$data'")
            Log.e(TAG, "Error in onDataReceived (Size:$packetSize, Data:'$data')", e)
        }
    }

    override fun onDebugMessage(message: String) {
        // BleConnectionManager로부터 디버그 메시지를 수신하여 TextView에 표시
        appendToDebugLog("BLE_MAN: $message")
    }
}
