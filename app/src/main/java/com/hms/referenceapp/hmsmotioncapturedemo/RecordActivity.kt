package com.hms.referenceapp.hmsmotioncapturedemo

import android.content.Context
import android.content.Intent
import android.hardware.Camera
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.hms.referenceapp.hmsmotioncapturedemo.camera.BoneGLSurfaceView
import com.hms.referenceapp.hmsmotioncapturedemo.camera.CameraSource
import com.hms.referenceapp.hmsmotioncapturedemo.camera.CameraSourcePreview
import com.hms.referenceapp.hmsmotioncapturedemo.skeleton.LocalSkeletonProcessor
import com.hms.referenceapp.hmsmotioncapturedemo.utils.MyConfigChooser
import java.io.IOException

class RecordActivity : AppCompatActivity() {
    private val tag = "HumanSkeletonActivity"
    private var cameraSource: CameraSource? = null
    private var cameraSourcePreview: CameraSourcePreview? = null
    private lateinit var glLayout: RelativeLayout
    private lateinit var localSkeletonProcessor: LocalSkeletonProcessor
    private lateinit var boneRenderManager: BoneGLSurfaceView
    private lateinit var glSurfaceView: GLSurfaceView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_record)
        cameraSourcePreview = findViewById(R.id.firePreview)
        glLayout = findViewById(R.id.rl_add_surface)
        localSkeletonProcessor = LocalSkeletonProcessor(this@RecordActivity)
        val facingSwitch = findViewById<ToggleButton>(R.id.facingSwitch)
        facingSwitch.setOnCheckedChangeListener { _, isChecked ->
            Log.d(tag, "Set facing")
            if (cameraSource != null) {
                if (isChecked) {
                    cameraSource?.setFacing(CameraSource.CAMERA_FACING_FRONT)
                } else {
                    cameraSource?.setFacing(CameraSource.CAMERA_FACING_BACK)
                }
                localSkeletonProcessor.detector
            }
            cameraSourcePreview?.stop()
            startCameraSource()
        }
        if (Camera.getNumberOfCameras() == 1) {
            facingSwitch.visibility = View.GONE
        }
        glSurfaceView = GLSurfaceView(this)
        boneRenderManager = BoneGLSurfaceView()
        glSurfaceView.preserveEGLContextOnPause = true
        glSurfaceView.setEGLConfigChooser(MyConfigChooser())
        glSurfaceView.setEGLContextClientVersion(3)
        glSurfaceView.setRenderer(boneRenderManager)
        glLayout.addView(glSurfaceView)
        createCameraSource()
    }

    private fun createCameraSource() {
        // If there's no existing cameraSource, create one.
        if (cameraSource == null) {
            cameraSource = CameraSource(this, boneRenderManager)
        }
        cameraSource?.setMachineLearningFrameProcessor(localSkeletonProcessor)
    }

    private fun startCameraSource() {
        if (cameraSource != null) {
            try {
                cameraSource?.setRequestedPreviewWidth(CameraSource.WIDTH_SIZE)
                cameraSource?.setRequestedPreviewHeight(CameraSource.HEIGHT_SIZE)
                cameraSourcePreview?.start(cameraSource, glSurfaceView)
            } catch (e: IOException) {
                Log.e(tag, "Unable to start camera source.", e)
                cameraSource?.release()
                cameraSource = null
            }
        }
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        cameraSourcePreview?.stop()
        createCameraSource()
        startCameraSource()
    }

    override fun onResume() {
        super.onResume()
        Log.d(tag, "onResume")
        startCameraSource()
    }

    override fun onStop() {
        super.onStop()
        cameraSourcePreview?.stop()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (cameraSource != null) {
            cameraSource?.release()
        }
        localSkeletonProcessor.stop()
    }

    companion object {
        @JvmStatic
        fun start(context: Context) {
            val starter = Intent(context, RecordActivity::class.java)
            context.startActivity(starter)
        }
    }
}