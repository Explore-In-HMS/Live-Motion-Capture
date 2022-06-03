package com.hms.referenceapp.hmsmotioncapturedemo

import android.Manifest
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.vmadalin.easypermissions.EasyPermissions

class MainActivity : AppCompatActivity() {
    lateinit var btnStartLive: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        btnStartLive = findViewById(R.id.btnStartLive)
        btnStartLive.setOnClickListener {
            if (EasyPermissions.hasPermissions(this, Manifest.permission.CAMERA)) {
                RecordActivity.start(this)
            } else {
                EasyPermissions.requestPermissions(
                    this,
                    "To use the app, you need to allow permissions",
                    0x01 shl 8,
                    Manifest.permission.CAMERA
                )
            }
        }
    }
}