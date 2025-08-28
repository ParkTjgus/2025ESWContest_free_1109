package com.example.app.exercise // 실제 패키지명으로 변경하세요

import android.util.Log
// import com.example.app.ExerciseManager // No need to import itself

// ExerciseItem data class (ensure it's defined or imported)
// 예시: data class ExerciseItem(val name: String, val sets: Int, val reps: Int, val roundTripTime: Int, val restTime: Int)

enum class SessionState {
    IDLE,       // 세션 시작 전 대기 상태, 또는 한 운동 항목 완료 후 다음 시작 전 대기
    WORKING,    // 운동 세트 진행 중
    RESTING,    // 세트 사이 휴식 중
    FINISHED    // 모든 운동 세션 완료
}

object ExerciseManager {
    private var exerciseList: List<ExerciseItem> = emptyList()
    private var currentExerciseIndex: Int = -1 // -1 signifies no exercise from the list is active/selected yet
    private var currentSet: Int = 1
    var state: SessionState = SessionState.IDLE
        private set

    fun startExerciseSession(plan: List<ExerciseItem>) {
        Log.d("ExerciseManager", "startExerciseSession called. Plan size: ${plan.size}")
        exerciseList = ArrayList(plan) // Store a copy

        if (exerciseList.isNotEmpty()) {
            currentExerciseIndex = 0 // Point to the first exercise
            currentSet = 1
            state = SessionState.WORKING // Directly set to WORKING for the first exercise
            Log.d("ExerciseManager", "First exercise '${exerciseList[0].name}' set to WORKING. Index: $currentExerciseIndex, State: $state")
        } else {
            currentExerciseIndex = -1 // No exercises in the plan
            state = SessionState.IDLE // Or FINISHED if an empty plan means finished. IDLE seems safer.
            Log.w("ExerciseManager", "Empty plan provided. State set to IDLE.")
        }
    }

    fun getCurrentExercise(): ExerciseItem? {
        // Log.d("ExerciseManager", "getCurrentExercise called. Index: $currentExerciseIndex, List size: ${exerciseList.size}")
        if (currentExerciseIndex in exerciseList.indices) {
            return exerciseList[currentExerciseIndex]
        }
        // Log.d("ExerciseManager", "Returning null from getCurrentExercise. Index: $currentExerciseIndex")
        return null
    }

    /**
     * 한 세트가 완료된 후 호출됩니다.
     * 현재 운동의 다음 세트로 가거나, 현재 운동이 완료되면 상태를 IDLE로 변경합니다.
     */
    fun moveToNextStep() {
        val currentExercise = getCurrentExercise() ?: run {
            // This case implies no valid current exercise, might happen if clear() was called or plan was empty.
            state = if (exerciseList.isEmpty() && currentExerciseIndex == -1) SessionState.IDLE else SessionState.FINISHED
            Log.d("ExerciseManager", "moveToNextStep: No current exercise. State set to $state.")
            return
        }
        Log.d("ExerciseManager", "moveToNextStep for '${currentExercise.name}'. CurrentSet: $currentSet / ${currentExercise.sets}")

        // 현재 운동의 마지막 세트가 아니라면 -> 휴식 상태로 전환하고 다음 세트로.
        if (currentSet < currentExercise.sets) {
            currentSet++
            state = SessionState.RESTING // 휴식 후 같은 운동의 다음 세트 진행
            Log.d("ExerciseManager", "Moved to RESTING for next set. Next set: $currentSet for ${currentExercise.name}")
        }
        // 현재 운동의 마지막 세트였다면 -> 이 운동 항목 완료.
        else {
            // 현재 ExerciseItem 완료. ExerciseSetActivity가 종료되도록 IDLE 상태로 변경.
            // DeviceControlActivity가 다음 START_REQ를 받으면 prepareAndStartNextExercise()를 호출하여 다음 운동으로 진행.
            state = SessionState.IDLE
            Log.i("ExerciseManager", "Exercise '${currentExercise.name}' completed all sets. State set to IDLE. Waiting for external trigger to start next exercise.")
            // currentExerciseIndex는 여기서 증가시키지 않음. prepareAndStartNextExercise()가 담당.
        }
    }

    /**
     * DeviceControlActivity가 다음 운동을 시작하라는 START_REQ를 받았을 때 호출됩니다.
     * 다음 운동으로 진행하고 상태를 WORKING으로 설정합니다.
     * @return true 만약 다음 운동이 성공적으로 시작되면, false 모든 운동이 완료되었으면.
     */
    fun prepareAndStartNextExercise(): Boolean {
        // 이 함수는 이전 운동이 완료되어 state가 IDLE이거나,
        // 또는 아직 운동이 시작되지 않은 초기 상태(currentExerciseIndex = -1)일 때 호출될 것으로 예상.
        if (state != SessionState.IDLE && currentExerciseIndex >= 0) { // currentExerciseIndex >=0 checks if a plan was active
            Log.w("ExerciseManager", "prepareAndStartNextExercise called when state is $state, not IDLE. Current Index: $currentExerciseIndex. Proceeding cautiously or aborting might be needed depending on exact flow.")
            // If state is WORKING, it implies a START_REQ might be redundant or an issue.
            // For now, let's allow it to proceed if it means to advance.
        }

        // currentExerciseIndex가 -1 (초기) 또는 이전 운동 인덱스를 가리키고 있으므로, 다음 운동을 위해 증가.
        val nextExerciseAttemptIndex = if (currentExerciseIndex == -1 && exerciseList.isNotEmpty()) 0 else currentExerciseIndex + 1

        Log.d("ExerciseManager", "prepareAndStartNextExercise: Current index before advance: $currentExerciseIndex. Attempting index: $nextExerciseAttemptIndex")


        if (nextExerciseAttemptIndex >= exerciseList.size) {
            state = SessionState.FINISHED
            Log.i("ExerciseManager", "prepareAndStartNextExercise: All exercises in the plan are finished. State: FINISHED.")
            return false // 더 이상 진행할 운동 없음
        } else {
            currentExerciseIndex = nextExerciseAttemptIndex
            currentSet = 1 // 새 운동 시작이므로 세트 1로 초기화
            state = SessionState.WORKING
            val nextExercise = exerciseList[currentExerciseIndex]
            Log.i("ExerciseManager", "prepareAndStartNextExercise: Starting next exercise '${nextExercise.name}'. Index: $currentExerciseIndex, State: WORKING, Set: $currentSet")
            return true // 다음 운동 준비 완료
        }
    }

    /**
     * 휴식이 끝났음을 알리고, 상태를 다시 운동 중으로 변경합니다.
     * (주로 같은 운동의 세트 간 휴식 후 호출)
     */
    fun finishRest() {
        if (state == SessionState.RESTING) {
            // 휴식 후에는 항상 WORKING 상태로 돌아가 현재 currentExercise의 다음 세트/동작을 수행합니다.
            // currentExerciseIndex는 finishRest에서 변경되지 않습니다.
            state = SessionState.WORKING
            val currentExerciseName = getCurrentExercise()?.name ?: "Unknown Exercise"
            Log.d("ExerciseManager", "Rest finished. State set to WORKING for exercise '$currentExerciseName', set $currentSet.")
        } else {
            Log.w("ExerciseManager", "finishRest called but state is not RESTING. Current State: $state")
        }
    }

    fun getCurrentSetInfo(): String {
        val exercise = getCurrentExercise()
        return if (exercise != null) {
            "$currentSet / ${exercise.sets} 세트"
        } else {
            // Log.w("ExerciseManager", "getCurrentSetInfo called but no current exercise.")
            ""
        }
    }

    fun isSessionFinished(): Boolean {
        return state == SessionState.FINISHED
    }

    fun clear() {
        Log.d("ExerciseManager", "clear() called", Exception()) // 스택 트레이스 포함 로그
        exerciseList = emptyList()
        currentExerciseIndex = -1
        currentSet = 1
        state = SessionState.IDLE
    }

    // 디버깅용 함수는 유지
    fun getExerciseListSizeForDebug(): Int {
        return exerciseList.size
    }
}
