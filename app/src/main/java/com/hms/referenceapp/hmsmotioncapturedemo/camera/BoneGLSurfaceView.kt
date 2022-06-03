/*
 * Copyright 2022. Huawei Technologies Co., Ltd. All rights reserved.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.hms.referenceapp.hmsmotioncapturedemo.camera

import android.opengl.GLES20
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import com.hms.referenceapp.hmsmotioncapturedemo.utils.ShaderUtils
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class BoneGLSurfaceView : GLSurfaceView.Renderer {
    private var hasBoneData = false
    private var shaderProgram = 0
    private var uniformModelViewProjection = 0
    private var uniformColor = 0
    private var modelViewProjectionBuffer: FloatBuffer? = null
    private val vertexBuffer: FloatBuffer
    private val jointsPositions = FloatArray(24 * 3)
    private val leapedPositions = FloatArray(24 * 3)
    private val bonePairs = arrayOf(
        intArrayOf(0, 3),
        intArrayOf(3, 6),
        intArrayOf(6, 9),
        intArrayOf(9, 12),
        intArrayOf(12, 15),
        intArrayOf(0, 2),
        intArrayOf(2, 5),
        intArrayOf(5, 8),
        intArrayOf(8, 11),
        intArrayOf(9, 14),
        intArrayOf(14, 17),
        intArrayOf(17, 19),
        intArrayOf(19, 21),
        intArrayOf(21, 23),
        intArrayOf(0, 1),
        intArrayOf(1, 4),
        intArrayOf(4, 7),
        intArrayOf(7, 10),
        intArrayOf(9, 13),
        intArrayOf(13, 16),
        intArrayOf(16, 18),
        intArrayOf(18, 20),
        intArrayOf(20, 22)
    )
    private val bonePositions = FloatArray(bonePairs.size * 2 * 3)

    override fun onSurfaceCreated(gl: GL10, config: EGLConfig) {
        GLES30.glClearColor(0.9f, 0.9f, 0.9f, 1.0f)
        val vertexShader = """
            #version 300 es 
            layout (location = 0) in vec4 vPosition;
            uniform mat4 uModelViewProj;
            void main() { 
            gl_Position = vPosition * uModelViewProj;
            }
            
            """.trimIndent()
        val fragmentShader = """
            #version 300 es 
            precision mediump float;
            out vec4 fragColor;
            uniform vec3 uColor;void main() { 
            fragColor = vec4(uColor, 0.0); 
            }
            
            """.trimIndent()
        shaderProgram = ShaderUtils.createProgram(vertexShader, fragmentShader)
        uniformModelViewProjection = GLES30.glGetUniformLocation(shaderProgram, "uModelViewProj")
        uniformColor = GLES30.glGetUniformLocation(shaderProgram, "uColor")
        GLES30.glLineWidth(5f)
    }

    override fun onSurfaceChanged(gl: GL10, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        val aspectRatio = width.toFloat() / height
        val viewMatrix = FloatArray(16)
        val projMatrix = FloatArray(16)
        val modelMatrix = FloatArray(16)
        val modelViewMatrix = FloatArray(16)
        val modelViewProjection = FloatArray(16)
        Matrix.setLookAtM(viewMatrix, 0, 0f, 0f, 4.5f, 0f, 0f, 0f, 0f, 1f, 0f)
        Matrix.perspectiveM(projMatrix, 0, 25.0f, aspectRatio, 0.3f, 1000f)
        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.scaleM(modelMatrix, 0, 1f, 1f, 1f)
        Matrix.multiplyMM(modelViewMatrix, 0, viewMatrix, 0, modelMatrix, 0)
        Matrix.multiplyMM(modelViewProjection, 0, projMatrix, 0, modelViewMatrix, 0)
        modelViewProjectionBuffer = ByteBuffer.allocateDirect(modelViewProjection.size * 4).order(
            ByteOrder.nativeOrder()
        ).asFloatBuffer()
        modelViewProjectionBuffer?.put(modelViewProjection)
        modelViewProjectionBuffer?.position(0)
    }

    override fun onDrawFrame(gl: GL10) {
        GLES30.glUseProgram(shaderProgram)
        GLES20.glClear(GL10.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        if (!hasBoneData) {
            return
        }
        GLES30.glUniformMatrix4fv(uniformModelViewProjection, 1, false, modelViewProjectionBuffer)
        GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, 0, vertexBuffer)
        GLES30.glEnableVertexAttribArray(0)
        updateVertexBuffer()
        GLES30.glUniform3f(uniformColor, 1.0f, 0.0f, 0.0f)
        GLES30.glDrawArrays(GLES30.GL_LINES, 0, MIDDLE_BONE_COUNT)
        GLES30.glUniform3f(uniformColor, 0.0f, 1.0f, 0.0f)
        GLES30.glDrawArrays(GLES30.GL_LINES, MIDDLE_BONE_COUNT, LEFT_BONE_COUNT)
        GLES30.glUniform3f(uniformColor, 0.0f, 0.0f, 1.0f)
        GLES30.glDrawArrays(GLES30.GL_LINES, MIDDLE_BONE_COUNT + LEFT_BONE_COUNT, RIGHT_BONE_COUNT)
    }

    fun setData(joints: List<List<Float>>?, trans: List<Float>) {
        if (joints == null) {
            hasBoneData = false
            return
        }
        hasBoneData = true
        var index = 0
        for (i in joints.indices) {
            for (j in joints[i].indices) {
                when (j) {
                    1 -> {
                        jointsPositions[index] = -joints[i][j] - trans[j]
                    }
                    0 -> {
                        jointsPositions[index] = joints[i][j] + trans[j]
                    }
                    else -> {
                        jointsPositions[index] = joints[i][j]
                    }
                }
                index++
            }
        }
    }

    private fun updateVertexBuffer() {
        // Interpolation
        for (i in jointsPositions.indices) {
            leapedPositions[i] = leap(leapedPositions[i], jointsPositions[i])
        }

        // Get the vertex array of the bone connection
        var index = 0
        for (bonePair in bonePairs) {
            bonePositions[index++] = leapedPositions[bonePair[0] * 3]
            bonePositions[index++] = leapedPositions[bonePair[0] * 3 + 1]
            bonePositions[index++] = leapedPositions[bonePair[0] * 3 + 2]
            bonePositions[index++] = leapedPositions[bonePair[1] * 3]
            bonePositions[index++] = leapedPositions[bonePair[1] * 3 + 1]
            bonePositions[index++] = leapedPositions[bonePair[1] * 3 + 2]
        }
        vertexBuffer.clear()
        vertexBuffer.put(bonePositions)
        vertexBuffer.position(0)
    }

    private fun leap(a: Float, b: Float): Float {
        return a + (b - a) * 0.1.toFloat()
    }

    companion object {
        // Different colors for the middle, left and right bones
        private const val MIDDLE_BONE_COUNT = 10
        private const val LEFT_BONE_COUNT = 18
        private const val RIGHT_BONE_COUNT = 18
    }

    init {
        vertexBuffer =
            ByteBuffer.allocateDirect(bonePositions.size * 4).order(ByteOrder.nativeOrder())
                .asFloatBuffer()
    }
}