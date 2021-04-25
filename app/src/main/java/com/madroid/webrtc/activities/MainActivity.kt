package com.madroid.webrtc.activities

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import com.madroid.webrtc.R

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
    }

    fun joinVideoCall(view: View) {
        startActivity(Intent(this, CallActivity::class.java))
    }

    fun joinChat(view: View) {
        startActivity(Intent(this, ChatActivity::class.java))
    }
}