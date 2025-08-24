package com.example.app.exercise

import android.os.Bundle
import android.os.CountDownTimer
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.app.R
import com.example.app.ExerciseManager
import com.example.app.SessionState
import java.util.concurrent.TimeUnit

class RestTimerActivity : AppCompatActivity() {

    private lateinit var restTimeTextView: TextView
    private lateinit var skipRestButton: Button
    private var restTimer: CountDownTimer? = null
    private var restTimeSeconds: Long = 30 // 기본 휴식 시간 (초)

    companion object {
        const val EXTRA_REST_TIME_SECONDS = "extra_rest_time_seconds"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_rest_timer)

        restTimeTextView = findViewById(R.id.restTimeTextView)
        skipRestButton = findViewById(R.id.skipRestButton)

        restTimeSeconds = intent.getLongExtra(EXTRA_REST_TIME_SECONDS, 30)
        Log.d("RestTimerActivity", "Received rest time: $restTimeSeconds seconds")

        if (restTimeSeconds <= 0) {
            Toast.makeText(this, "유효한 휴식 시간이 아닙니다.", Toast.LENGTH_SHORT).show()
            Log.e("RestTimerActivity", "Invalid rest time: $restTimeSeconds, finishing activity.")
            finish()
            return
        }

        skipRestButton.setOnClickListener {
            restTimer?.cancel()
            ExerciseManager.finishRest()
            Log.d("RestTimerActivity", "Skip button clicked, finishing rest.")
            finish()
        }

        startRestTimer(restTimeSeconds)
    }

    private fun startRestTimer(seconds: Long) {
        restTimer?.cancel() // 기존 타이머 취소
        restTimer = object : CountDownTimer(seconds * 1000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val minutes = TimeUnit.MILLISECONDS.toMinutes(millisUntilFinished)
                val remainingSeconds = TimeUnit.MILLISECONDS.toSeconds(millisUntilFinished) - TimeUnit.MINUTES.toSeconds(minutes)
                restTimeTextView.text = String.format("%02d:%02d", minutes, remainingSeconds)
                Log.d("RestTimerActivity", "Timer tick: ${restTimeTextView.text}")
            }

            override fun onFinish() {
                restTimeTextView.text = "00:00"
                ExerciseManager.finishRest()
                Log.d("RestTimerActivity", "Timer finished, finishing rest.")
                finish()
            }
        }.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        restTimer?.cancel()
        Log.d("RestTimerActivity", "onDestroy called, timer cancelled.")
    }
}