package com.example.app.exercise

data class ExerciseItem(
    val name: String,
    val sets: Int,
    val reps: Int,
    val roundTripTime: Int,
    val restTime: Int
)