package com.example.app

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class TestMainActivity : AppCompatActivity() {
    private lateinit var btnExercise: Button


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_test_main)

        btnExercise = findViewById<Button>(R.id.exerciseBtn)
        btnExercise.setOnClickListener { startActivity(Intent(this, DailyPlanActivity::class.java)) }

    }
}