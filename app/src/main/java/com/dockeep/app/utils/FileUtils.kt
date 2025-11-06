package com.dockeep.app.utils

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.collections.mutableSetOf

class FileUtils {
    companion object {
        private const val TAG = "FileUtils"

        fun getUriForFile(context: Context, file: File): Uri {
            return FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
        }

        fun createZipFile(context: Context, docName: String, imagePaths: List<String>): File? {
            return try {
                val exportsDir = getDockeepDirectory(context)
                if (!exportsDir.exists()) {
                    exportsDir.mkdirs()
                }

                val timeStamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())
                val zipFileName = "${docName}_$timeStamp.zip"
                val zipFile = File(exportsDir, zipFileName)

                val zipOutputStream = ZipOutputStream(FileOutputStream(zipFile))
                
                // Keep track of entry names to avoid duplicates
                val entryNames = mutableSetOf<String>()
                var counter = 1

                for (imagePath in imagePaths) {
                    val imageFile = File(imagePath)
                    if (imageFile.exists()) {
                        // Get the original file name
                        var fileName = imageFile.name
                        
                        // If we've already used this file name, create a unique name
                        var uniqueFileName = fileName
                        while (entryNames.contains(uniqueFileName)) {
                            val extension = fileName.substringAfterLast(".", "")
                            val nameWithoutExtension = fileName.substringBeforeLast(".", fileName)
                            uniqueFileName = "${nameWithoutExtension}_${counter++}.$extension"
                        }
                        
                        // Add to our tracking set
                        entryNames.add(uniqueFileName)
                        
                        val entry = ZipEntry(uniqueFileName)
                        zipOutputStream.putNextEntry(entry)

                        val fileInputStream = FileInputStream(imageFile)
                        val buffer = ByteArray(1024)
                        var length: Int
                        while (fileInputStream.read(buffer).also { length = it } > 0) {
                            zipOutputStream.write(buffer, 0, length)
                        }
                        fileInputStream.close()
                        zipOutputStream.closeEntry()
                    }
                }

                zipOutputStream.close()
                zipFile
            } catch (e: Exception) {
                Log.e(TAG, "Error creating zip file", e)
                null
            }
        }
        
        fun createHierarchicalZipFile(context: Context, userName: String, mainUserDocuments: Map<String, List<String>>, personDocuments: Map<String, Map<String, List<String>>>): File? {
            return try {
                val exportsDir = getDockeepDirectory(context)
                if (!exportsDir.exists()) {
                    exportsDir.mkdirs()
                }

                val timeStamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())
                val zipFileName = "${userName}_Documents_$timeStamp.zip"
                val zipFile = File(exportsDir, zipFileName)

                val zipOutputStream = ZipOutputStream(FileOutputStream(zipFile))
                
                // Keep track of entry names to avoid duplicates
                val entryNames = mutableSetOf<String>()

                // Add main user documents with user name prefix
                for ((documentName, imagePaths) in mainUserDocuments) {
                    // Sanitize document name to remove invalid characters for folder names
                    val sanitizedDocumentName = documentName.replace("[^a-zA-Z0-9\\-_ ]".toRegex(), "_")
                    
                    // Prefix document name with user's name
                    val prefixedDocumentName = "$userName's $sanitizedDocumentName"
                    
                    // Create folder entry for the document even if it has no images
                    val folderEntryName = "$prefixedDocumentName/"
                    if (!entryNames.contains(folderEntryName)) {
                        entryNames.add(folderEntryName)
                        val folderEntry = ZipEntry(folderEntryName)
                        zipOutputStream.putNextEntry(folderEntry)
                        zipOutputStream.closeEntry()
                    }
                    
                    // Keep track of image names within this document to avoid duplicates
                    val documentImageNames = mutableSetOf<String>()
                    var imageCounter = 1
                    
                    // Add each image to the zip file in a folder named after the document
                    for (imagePath in imagePaths) {
                        val imageFile = File(imagePath)
                        // Only include files that actually exist and are associated with current documents
                        if (imageFile.exists()) {
                            // Get the original file name
                            var fileName = imageFile.name
                            
                            // If we've already used this file name in this document, create a unique name
                            if (documentImageNames.contains(fileName)) {
                                val extension = fileName.substringAfterLast(".", "")
                                val nameWithoutExtension = fileName.substringBeforeLast(".", fileName)
                                fileName = "${nameWithoutExtension}_${imageCounter++}.$extension"
                            }
                            
                            // Add to our tracking set
                            documentImageNames.add(fileName)
                            
                            // Create a zip entry with folder structure: UserName's DocumentName/image.jpg
                            val entryName = "$prefixedDocumentName/$fileName"
                            
                            // If this entry name already exists in the zip, create a unique one
                            var uniqueEntryName = entryName
                            var counter = 1
                            while (entryNames.contains(uniqueEntryName)) {
                                val extension = fileName.substringAfterLast(".", "")
                                val nameWithoutExtension = fileName.substringBeforeLast(".", fileName)
                                val uniqueFileName = "${nameWithoutExtension}_${counter++}.$extension"
                                uniqueEntryName = "$prefixedDocumentName/$uniqueFileName"
                            }
                            
                            // Add to our entry names set
                            entryNames.add(uniqueEntryName)
                            
                            val entry = ZipEntry(uniqueEntryName)
                            zipOutputStream.putNextEntry(entry)
                            
                            // Copy file contents to zip
                            val buffer = ByteArray(1024)
                            val fileInputStream = FileInputStream(imageFile)
                            var length: Int
                            while (fileInputStream.read(buffer).also { length = it } > 0) {
                                zipOutputStream.write(buffer, 0, length)
                            }
                            fileInputStream.close()
                            zipOutputStream.closeEntry()
                        }
                    }
                }
                
                // Create the FriendsAndFamily root folder
                val friendsAndFamilyRootEntryName = "FriendsAndFamily/"
                if (!entryNames.contains(friendsAndFamilyRootEntryName)) {
                    entryNames.add(friendsAndFamilyRootEntryName)
                    val rootEntry = ZipEntry(friendsAndFamilyRootEntryName)
                    zipOutputStream.putNextEntry(rootEntry)
                    zipOutputStream.closeEntry()
                }
                
                // Add person documents with hierarchical structure: FriendsAndFamily/PersonName/DocumentName/Image.jpg
                // (Keeping the friends and family structure unchanged as requested)
                for ((personName, documents) in personDocuments) {
                    // Sanitize person name
                    val sanitizedPersonName = personName.replace("[^a-zA-Z0-9\\-_ ]".toRegex(), "_")
                    
                    // Create the person folder if it doesn't exist
                    val personFolderEntryName = "FriendsAndFamily/$sanitizedPersonName/"
                    if (!entryNames.contains(personFolderEntryName)) {
                        entryNames.add(personFolderEntryName)
                        val personFolderEntry = ZipEntry(personFolderEntryName)
                        zipOutputStream.putNextEntry(personFolderEntry)
                        zipOutputStream.closeEntry()
                    }
                    
                    // Add documents for this person (even if empty)
                    for ((documentName, imagePaths) in documents) {
                        // Sanitize document name
                        val sanitizedDocumentName = documentName.replace("[^a-zA-Z0-9\\-_ ]".toRegex(), "_")
                        
                        // Create folder entry for the document even if it has no images
                        val folderEntryName = "FriendsAndFamily/$sanitizedPersonName/$sanitizedDocumentName/"
                        if (!entryNames.contains(folderEntryName)) {
                            entryNames.add(folderEntryName)
                            val folderEntry = ZipEntry(folderEntryName)
                            zipOutputStream.putNextEntry(folderEntry)
                            zipOutputStream.closeEntry()
                        }
                        
                        // Keep track of image names within this document to avoid duplicates
                        val documentImageNames = mutableSetOf<String>()
                        var imageCounter = 1
                        
                        // Add each image to the zip file in a folder named after the document
                        for (imagePath in imagePaths) {
                            val imageFile = File(imagePath)
                            // Only include files that actually exist and are associated with current documents
                            if (imageFile.exists()) {
                                // Get the original file name
                                var fileName = imageFile.name
                                
                                // If we've already used this file name in this document, create a unique name
                                if (documentImageNames.contains(fileName)) {
                                    val extension = fileName.substringAfterLast(".", "")
                                    val nameWithoutExtension = fileName.substringBeforeLast(".", fileName)
                                    fileName = "${nameWithoutExtension}_${imageCounter++}.$extension"
                                }
                                
                                // Add to our tracking set
                                documentImageNames.add(fileName)
                                
                                // Create a zip entry with folder structure: FriendsAndFamily/PersonName/DocumentName/image.jpg
                                val entryName = "FriendsAndFamily/$sanitizedPersonName/$sanitizedDocumentName/$fileName"
                                
                                // If this entry name already exists in the zip, create a unique one
                                var uniqueEntryName = entryName
                                var counter = 1
                                while (entryNames.contains(uniqueEntryName)) {
                                    val extension = fileName.substringAfterLast(".", "")
                                    val nameWithoutExtension = fileName.substringBeforeLast(".", fileName)
                                    val uniqueFileName = "${nameWithoutExtension}_${counter++}.$extension"
                                    uniqueEntryName = "FriendsAndFamily/$sanitizedPersonName/$sanitizedDocumentName/$uniqueFileName"
                                }
                                
                                // Add to our entry names set
                                entryNames.add(uniqueEntryName)
                                
                                val entry = ZipEntry(uniqueEntryName)
                                zipOutputStream.putNextEntry(entry)
                                
                                // Copy file contents to zip
                                val buffer = ByteArray(1024)
                                val fileInputStream = FileInputStream(imageFile)
                                var length: Int
                                while (fileInputStream.read(buffer).also { length = it } > 0) {
                                    zipOutputStream.write(buffer, 0, length)
                                }
                                fileInputStream.close()
                                zipOutputStream.closeEntry()
                            }
                        }
                    }
                }
                
                zipOutputStream.close()
                zipFile
            } catch (e: Exception) {
                Log.e(TAG, "Error creating hierarchical zip file", e)
                null
            }
        }

        fun deleteDocumentFolder(context: Context, docName: String) {
            val docDir = File(getDockeepDirectory(context), docName)
            if (docDir.exists()) {
                docDir.deleteRecursively()
            }
        }

        fun saveImageToDocumentFolder(context: Context, documentId: Long, uri: Uri, order: Int = 0): String? {
            return try {
                val docDir = getDockeepDirectory(context)
                if (!docDir.exists()) {
                    docDir.mkdirs()
                }
                
                // Generate a unique filename using timestamp and random number
                val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.getDefault()).format(Date())
                val random = (Math.random() * 1000).toInt()
                val imageFile = File(docDir, "$documentId-$timeStamp-$random.jpg")

                val inputStream = context.contentResolver.openInputStream(uri)
                val outputStream = FileOutputStream(imageFile)

                inputStream?.use { input ->
                    outputStream.use { output ->
                        input.copyTo(output)
                    }
                }

                imageFile.absolutePath
            } catch (e: Exception) {
                Log.e(TAG, "Error saving image to document folder", e)
                null
            }
        }

        private fun getDockeepDirectory(context: Context): File {
            return File(context.getExternalFilesDir(null), "DocKeep")
        }
    }
}