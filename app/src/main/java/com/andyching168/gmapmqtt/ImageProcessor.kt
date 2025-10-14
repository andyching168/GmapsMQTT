package com.andyching168.gmapmqtt

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import java.io.ByteArrayOutputStream

/**
 * 圖像處理器：用於壓縮和編碼通知圖標
 * 
 * 處理流程：
 * 1. 降維與預處理 (Downscale → Grayscale)
 * 2. 圖像分析與分類 (雙閾值分類)
 * 3. 數據壓縮與封裝 (RLE → Hex編碼)
 */
class ImageProcessor {
    
    companion object {
        private const val TAG = "ImageProcessor"
        private const val TARGET_SIZE = 32  // 目標尺寸 32x32
        private const val TOTAL_PIXELS = TARGET_SIZE * TARGET_SIZE  // 1024 像素
        
        /**
         * 處理圖像並返回壓縮編碼後的字串
         * 
         * @param bitmap 原始圖像（約 156x156 像素）
         * @return Hex 編碼的壓縮字串，失敗返回 null
         */
        fun processImage(bitmap: Bitmap?): String? {
            if (bitmap == null) {
                Log.w(TAG, "輸入的 Bitmap 為 null")
                return null
            }
            
            try {
                // 第一步：降維與預處理
                val downscaledBitmap = downscaleBitmap(bitmap)
                val grayscaleBitmap = convertToGrayscale(downscaledBitmap)
                
                // 第二步：圖像分析與分類
                val classificationArray = classifyPixels(grayscaleBitmap)
                
                // 第三步：數據壓縮與封裝
                val compressedBytes = runLengthEncode(classificationArray)
                val hexString = bytesToHex(compressedBytes)
                
                Log.d(TAG, """
                    圖像處理完成:
                    原始尺寸: ${bitmap.width}x${bitmap.height}
                    壓縮後尺寸: ${TARGET_SIZE}x${TARGET_SIZE}
                    分類陣列大小: ${classificationArray.size}
                    RLE 壓縮後: ${compressedBytes.size} bytes
                    Hex 字串長度: ${hexString.length} chars
                """.trimIndent())
                
                return hexString
                
            } catch (e: Exception) {
                Log.e(TAG, "圖像處理失敗", e)
                return null
            }
        }
        
        /**
         * 第一步 - 1: 將 Bitmap 縮放至 32x32 像素
         */
        private fun downscaleBitmap(bitmap: Bitmap): Bitmap {
            return Bitmap.createScaledBitmap(bitmap, TARGET_SIZE, TARGET_SIZE, true)
        }
        
        /**
         * 第一步 - 2: 將彩色 Bitmap 轉換為灰階
         */
        private fun convertToGrayscale(bitmap: Bitmap): Bitmap {
            val width = bitmap.width
            val height = bitmap.height
            val grayscaleBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            
            for (y in 0 until height) {
                for (x in 0 until width) {
                    val pixel = bitmap.getPixel(x, y)
                    
                    // 使用標準灰階轉換公式: 0.299R + 0.587G + 0.114B
                    val r = Color.red(pixel)
                    val g = Color.green(pixel)
                    val b = Color.blue(pixel)
                    val gray = (0.299 * r + 0.587 * g + 0.114 * b).toInt()
                    
                    grayscaleBitmap.setPixel(x, y, Color.rgb(gray, gray, gray))
                }
            }
            
            return grayscaleBitmap
        }
        
        /**
         * 第二步: 使用雙閾值對像素進行三態分類
         * 
         * @return IntArray[1024] - 每個元素為 0(背景)、1(灰色) 或 2(白色)
         */
        private fun classifyPixels(grayscaleBitmap: Bitmap): IntArray {
            val width = grayscaleBitmap.width
            val height = grayscaleBitmap.height
            val pixels = IntArray(TOTAL_PIXELS)
            val grayValues = IntArray(TOTAL_PIXELS)
            
            // 提取所有灰階值
            var index = 0
            for (y in 0 until height) {
                for (x in 0 until width) {
                    val pixel = grayscaleBitmap.getPixel(x, y)
                    grayValues[index] = Color.red(pixel)  // 灰階圖的 R=G=B，取任一即可
                    index++
                }
            }
            
            // 計算低閾值 T_low（整張圖的平均灰度）
            val tLow = grayValues.average().toInt()
            
            // 計算高閾值 T_high（前景像素的平均灰度）
            val foregroundValues = grayValues.filter { it > tLow }
            val tHigh = if (foregroundValues.isNotEmpty()) {
                foregroundValues.average().toInt()
            } else {
                tLow + 50  // 如果沒有前景，設定一個默認值
            }
            
            // 像素三態分類
            for (i in grayValues.indices) {
                pixels[i] = when {
                    grayValues[i] < tLow -> 0   // 背景
                    grayValues[i] < tHigh -> 1  // 灰色
                    else -> 2                    // 白色
                }
            }
            
            Log.d(TAG, """
                分類閾值:
                T_low (背景/前景): $tLow
                T_high (灰色/白色): $tHigh
                背景像素: ${pixels.count { it == 0 }}
                灰色像素: ${pixels.count { it == 1 }}
                白色像素: ${pixels.count { it == 2 }}
            """.trimIndent())
            
            return pixels
        }
        
        /**
         * 第三步 - 1: 行程長度編碼 (Run-Length Encoding)
         * 
         * 將 [0,0,0,0,1,1,2,2,2] 壓縮為 [[0,4], [1,2], [2,3]]
         * 然後轉換為 byte[] 格式: [value1, count1, value2, count2, ...]
         */
        private fun runLengthEncode(data: IntArray): ByteArray {
            if (data.isEmpty()) return ByteArray(0)
            
            val output = ByteArrayOutputStream()
            var currentValue = data[0]
            var count = 1
            
            for (i in 1 until data.size) {
                if (data[i] == currentValue && count < 255) {
                    // 相同值且計數未超過 255
                    count++
                } else {
                    // 值改變或計數達到上限，寫入當前的值-計數對
                    output.write(currentValue)
                    output.write(count)
                    currentValue = data[i]
                    count = 1
                }
            }
            
            // 寫入最後一組
            output.write(currentValue)
            output.write(count)
            
            return output.toByteArray()
        }
        
        /**
         * 第三步 - 2: 將 byte[] 轉換為 Hex 字串
         * 
         * 例如: [0x0F, 0x1A] -> "0F1A"
         */
        private fun bytesToHex(bytes: ByteArray): String {
            val hexChars = CharArray(bytes.size * 2)
            for (i in bytes.indices) {
                val v = bytes[i].toInt() and 0xFF
                hexChars[i * 2] = "0123456789ABCDEF"[v ushr 4]
                hexChars[i * 2 + 1] = "0123456789ABCDEF"[v and 0x0F]
            }
            return String(hexChars)
        }
        
        /**
         * 解碼函數：將 Hex 字串轉回 byte[]（用於驗證或還原）
         */
        fun hexToBytes(hex: String): ByteArray {
            val len = hex.length
            val data = ByteArray(len / 2)
            var i = 0
            while (i < len) {
                data[i / 2] = ((Character.digit(hex[i], 16) shl 4) + Character.digit(hex[i + 1], 16)).toByte()
                i += 2
            }
            return data
        }
        
        /**
         * 解碼函數：將 RLE 數據還原為原始分類陣列（用於驗證）
         */
        fun runLengthDecode(bytes: ByteArray): IntArray {
            val output = mutableListOf<Int>()
            var i = 0
            while (i < bytes.size) {
                val value = bytes[i].toInt() and 0xFF
                val count = bytes[i + 1].toInt() and 0xFF
                repeat(count) {
                    output.add(value)
                }
                i += 2
            }
            return output.toIntArray()
        }
    }
}
