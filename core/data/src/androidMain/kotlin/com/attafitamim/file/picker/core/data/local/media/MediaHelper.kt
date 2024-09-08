package com.attafitamim.file.picker.core.data.local.media

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.text.format.DateUtils
import com.attafitamim.file.picker.core.domain.model.media.MediaElement
import com.attafitamim.file.picker.core.domain.model.media.MediaResource
import com.attafitamim.file.picker.core.utils.SECOND_IN_MILLIS
import com.attafitamim.file.picker.core.utils.convertMillisToLocalDate
import com.attafitamim.file.picker.core.utils.isSdk29AndHigher
import com.attafitamim.file.picker.core.utils.tryCreateDirectoryIfNotExists
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object MediaHelper {

    private const val IMAGE_MIME_TYPE = "image/jpeg"
    private const val JPEG_FILE_EXTENSION = "jpg"
    private const val IMAGES_SAVING_FORMAT = "dd-MM-yyyy KK_mm_ss a"
    private const val BITMAP_QUALITY = 100

    suspend fun Context.insertImage(
        imageBytes: ByteArray,
        title: String,
        currentTime: Long,
        mimeType: String,
        description: String?,
        isDateEnabled: Boolean,
        appFolder: String
    ): MediaElement.ImageElement = withContext(Dispatchers.IO) {
        val source = BitmapFactory.decodeByteArray(
            imageBytes,
            0,
            imageBytes.size
        )

        val values = getContentValues(
            title,
            currentTime,
            description,
            isDateEnabled,
            mimeType,
            Environment.DIRECTORY_PICTURES,
            appFolder
        )

        val uri = insertImageToGallery(source, values)
        val resource = MediaResource(uri)
        val timeInSeconds = (currentTime / SECOND_IN_MILLIS).toInt()
        MediaElement.ImageElement(resource, mimeType, timeInSeconds)
    }

    suspend fun Context.insertMedia(
        path: String,
        mimeType: String,
        title: String,
        currentTime: Long,
        description: String?,
        isDateEnabled: Boolean,
        isPhoto: Boolean,
        appFolder: String
    ): MediaElement = withContext(Dispatchers.IO) {
        val systemFolder = if (isPhoto) {
            Environment.DIRECTORY_PICTURES
        } else {
            Environment.DIRECTORY_MOVIES
        }

        val values = getContentValues(
            title,
            currentTime,
            description,
            isDateEnabled,
            mimeType,
            systemFolder,
            appFolder
        )

        val uri = insertFileToGallery(path, values, isPhoto)
        val resource = MediaResource(uri)
        val timeInSeconds = (currentTime / SECOND_IN_MILLIS).toInt()
        MediaElement.ImageElement(resource, mimeType, timeInSeconds)
    }

    private fun Context.insertFileToGallery(
        originalFilePath: String,
        values: ContentValues,
        isPhoto: Boolean
    ): Uri {
        val externalUri = if (isPhoto) {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }
        val newFileUri = requireNotNull(contentResolver.insert(externalUri, values))
        val outputStream = contentResolver.openOutputStream(newFileUri)

        File(originalFilePath).inputStream().use { input ->
            outputStream.use { output ->
                requireNotNull(output)

                input.copyTo(output)
            }
        }

        return newFileUri
    }

    private fun Context.insertImageToGallery(source: Bitmap, values: ContentValues): Uri {
        val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            .let(::requireNotNull)

        val outputStream = contentResolver.openOutputStream(uri)
        outputStream?.use { stream ->
            source.compress(Bitmap.CompressFormat.JPEG, BITMAP_QUALITY, stream)
        }
        return uri
    }

    private fun getContentValues(
        title: String,
        currentTime: Long,
        description: String? = null,
        isDateEnabled: Boolean,
        mimeType: String?,
        systemFolder: String,
        appFolder: String
    ): ContentValues {
        val currentTimeFormatted = convertMillisToLocalDate(currentTime, IMAGES_SAVING_FORMAT)
        val correctTitle = if (isDateEnabled) "$title-$currentTimeFormatted" else title

        return ContentValues().apply {
            put(MediaStore.Images.Media.TITLE, currentTimeFormatted)
            put(MediaStore.Images.Media.DISPLAY_NAME, correctTitle)
            put(MediaStore.Images.Media.DESCRIPTION, description)
            put(MediaStore.Images.Media.MIME_TYPE, mimeType ?: IMAGE_MIME_TYPE)
            put(MediaStore.Images.Media.DATE_ADDED, currentTime / DateUtils.SECOND_IN_MILLIS)

            if (isSdk29AndHigher) {
                put(MediaStore.Images.Media.DATE_TAKEN, currentTime)
                put(
                    MediaStore.Images.Media.RELATIVE_PATH,
                    "$systemFolder/$appFolder"
                )
            } else {
                val picturesDirectory =
                    "${Environment.getExternalStorageDirectory().absolutePath}/$systemFolder"
                val isCreated = tryCreateDirectoryIfNotExists(picturesDirectory)

                if (isCreated) {
                    val pathToDirectory = "$picturesDirectory/$appFolder"
                    tryCreateDirectoryIfNotExists(pathToDirectory)
                    val extension = mimeType?.getExtensionFromMimeType() ?: JPEG_FILE_EXTENSION
                    put(
                        MediaStore.Images.Media.DATA,
                        "$pathToDirectory/$correctTitle.$extension"
                    )
                }
            }
        }
    }

    private fun String.getExtensionFromMimeType(): String = this.substringAfter('/')
}