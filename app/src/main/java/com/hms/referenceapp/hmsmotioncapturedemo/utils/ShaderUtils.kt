/*
 * Copyright 2022. Huawei Technologies Co., Ltd. All rights reserved.
 *
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.hms.referenceapp.hmsmotioncapturedemo.utils

import android.opengl.GLES30
import android.util.Log

object ShaderUtils {
    private const val TAG = "ShaderUtils"
    private fun compileShader(data: String, type: Int): Int {
        val id = GLES30.glCreateShader(type)
        GLES30.glShaderSource(id, data)
        GLES30.glCompileShader(id)
        val status = IntArray(1)
        GLES30.glGetShaderiv(id, GLES30.GL_COMPILE_STATUS, status, 0)
        if (0 == status[0]) {
            Log.e(TAG, GLES30.glGetShaderInfoLog(id))
            GLES30.glDeleteShader(id)
            return 0
        }
        return id
    }

    fun createProgram(vertexShader: String, fragmentShader: String): Int {
        val id = GLES30.glCreateProgram()
        val vertexShaderId = compileShader(vertexShader, GLES30.GL_VERTEX_SHADER)
        val fragmentShaderId = compileShader(fragmentShader, GLES30.GL_FRAGMENT_SHADER)
        GLES30.glAttachShader(id, vertexShaderId)
        GLES30.glAttachShader(id, fragmentShaderId)
        GLES30.glLinkProgram(id)
        val status = IntArray(1)
        GLES30.glGetProgramiv(id, GLES30.GL_LINK_STATUS, status, 0)
        if (0 == status[0]) {
            Log.e(TAG, GLES30.glGetProgramInfoLog(id))
            GLES30.glDeleteProgram(id)
            return 0
        }
        return id
    }
}