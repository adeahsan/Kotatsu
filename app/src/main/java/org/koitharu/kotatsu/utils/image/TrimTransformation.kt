package org.koitharu.kotatsu.utils.image

import android.graphics.Bitmap
import androidx.core.graphics.get
import coil.size.Size
import coil.transform.Transformation

class TrimTransformation : Transformation {

	override val cacheKey: String = javaClass.name

	override suspend fun transform(input: Bitmap, size: Size): Bitmap {
		var left = 0
		var top = 0
		var right = 0
		var bottom = 0

		// Left
		for (x in 0 until input.width) {
			var isColBlank = true
			val prevColor = input[x, 0]
			for (y in 1 until input.height) {
				if (input[x, y] != prevColor) {
					isColBlank = false
					break
				}
			}
			if (isColBlank) {
				left++
			} else {
				break
			}
		}
		if (left == input.width) {
			return input
		}
		// Right
		for (x in (left until input.width).reversed()) {
			var isColBlank = true
			val prevColor = input[x, 0]
			for (y in 1 until input.height) {
				if (input[x, y] != prevColor) {
					isColBlank = false
					break
				}
			}
			if (isColBlank) {
				right++
			} else {
				break
			}
		}
		// Top
		for (y in 0 until input.height) {
			var isRowBlank = true
			val prevColor = input[0, y]
			for (x in 1 until input.width) {
				if (input[x, y] != prevColor) {
					isRowBlank = false
					break
				}
			}
			if (isRowBlank) {
				top++
			} else {
				break
			}
		}
		// Bottom
		for (y in (top until input.height).reversed()) {
			var isRowBlank = true
			val prevColor = input[0, y]
			for (x in 1 until input.width) {
				if (input[x, y] != prevColor) {
					isRowBlank = false
					break
				}
			}
			if (isRowBlank) {
				bottom++
			} else {
				break
			}
		}

		return if (left != 0 || right != 0 || top != 0 || bottom != 0) {
			Bitmap.createBitmap(input, left, top, input.width - left - right, input.height - top - bottom)
		} else {
			input
		}
	}

	override fun equals(other: Any?) = other is TrimTransformation

	override fun hashCode() = javaClass.hashCode()
}