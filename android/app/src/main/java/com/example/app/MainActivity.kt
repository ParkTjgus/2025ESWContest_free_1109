package com.example.app

import android.os.Bundle
import android.content.Intent // Intent를 import 합니다.
import android.widget.Button // Button을 import 합니다.
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // 1. XML에 추가한 버튼을 ID로 찾아옵니다.
        val bleSearchButton: Button = findViewById(R.id.ble_search_button)

        // 2. 버튼에 클릭 리스너를 설정합니다.
        bleSearchButton.setOnClickListener {
            // 3. BleSearchingActivity로 이동하기 위한 "의도(Intent)"를 만듭니다.
            val intent = Intent(this, BleSearchingActivity::class.java)

            // 4. 만들어진 Intent를 실행하여 화면을 전환합니다.
            startActivity(intent)
        }
    }
}