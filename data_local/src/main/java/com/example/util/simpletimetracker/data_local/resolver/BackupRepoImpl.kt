package com.example.util.simpletimetracker.data_local.resolver

import android.content.ContentResolver
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.example.util.simpletimetracker.domain.extension.orZero
import com.example.util.simpletimetracker.domain.model.ActivityFilter
import com.example.util.simpletimetracker.domain.model.AppColor
import com.example.util.simpletimetracker.domain.model.Category
import com.example.util.simpletimetracker.domain.model.Record
import com.example.util.simpletimetracker.domain.model.RecordTag
import com.example.util.simpletimetracker.domain.model.RecordToRecordTag
import com.example.util.simpletimetracker.domain.model.RecordType
import com.example.util.simpletimetracker.domain.model.RecordTypeCategory
import com.example.util.simpletimetracker.domain.repo.ActivityFilterRepo
import com.example.util.simpletimetracker.domain.repo.CategoryRepo
import com.example.util.simpletimetracker.domain.repo.RecordRepo
import com.example.util.simpletimetracker.domain.repo.RecordTagRepo
import com.example.util.simpletimetracker.domain.repo.RecordToRecordTagRepo
import com.example.util.simpletimetracker.domain.repo.RecordTypeCategoryRepo
import com.example.util.simpletimetracker.domain.repo.RecordTypeRepo
import com.example.util.simpletimetracker.domain.resolver.BackupRepo
import com.example.util.simpletimetracker.domain.resolver.ResultCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.BufferedReader
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Do not change backup parts order, always add new to the end.
 * Otherwise previous version's backups will be broken.
 */
@Singleton
class BackupRepoImpl @Inject constructor(
    private val contentResolver: ContentResolver,
    private val recordTypeRepo: RecordTypeRepo,
    private val recordRepo: RecordRepo,
    private val categoryRepo: CategoryRepo,
    private val recordTypeCategoryRepo: RecordTypeCategoryRepo,
    private val recordToRecordTagRepo: RecordToRecordTagRepo,
    private val recordTagRepo: RecordTagRepo,
    private val activityFilterRepo: ActivityFilterRepo,
) : BackupRepo {

    override suspend fun saveBackupFile(uriString: String): ResultCode =
        withContext(Dispatchers.IO) {
            var fileDescriptor: ParcelFileDescriptor? = null
            var fileOutputStream: FileOutputStream? = null

            try {
                val uri = Uri.parse(uriString)
                fileDescriptor = contentResolver.openFileDescriptor(uri, "w")
                fileOutputStream = fileDescriptor?.fileDescriptor?.let(::FileOutputStream)

                // Write file identification
                val identificationBackupRow: String = BACKUP_IDENTIFICATION + "\n"
                fileOutputStream?.write(identificationBackupRow.toByteArray())

                // Write data
                recordTypeRepo.getAll().forEach {
                    fileOutputStream?.write(
                        it.let(::toBackupString).toByteArray()
                    )
                }
                recordRepo.getAll().forEach {
                    fileOutputStream?.write(
                        it.let(::toBackupString).toByteArray()
                    )
                }
                categoryRepo.getAll().forEach {
                    fileOutputStream?.write(
                        it.let(::toBackupString).toByteArray()
                    )
                }
                recordTypeCategoryRepo.getAll().forEach {
                    fileOutputStream?.write(
                        it.let(::toBackupString).toByteArray()
                    )
                }
                recordTagRepo.getAll().forEach {
                    fileOutputStream?.write(
                        it.let(::toBackupString).toByteArray()
                    )
                }
                recordToRecordTagRepo.getAll().forEach {
                    fileOutputStream?.write(
                        it.let(::toBackupString).toByteArray()
                    )
                }
                activityFilterRepo.getAll().forEach {
                    fileOutputStream?.write(
                        it.let(::toBackupString).toByteArray()
                    )
                }

                fileOutputStream?.close()
                fileDescriptor?.close()
                ResultCode.SUCCESS
            } catch (e: Exception) {
                Timber.e(e)
                ResultCode.ERROR
            } finally {
                try {
                    fileOutputStream?.close()
                    fileDescriptor?.close()
                } catch (e: IOException) {
                    // Do nothing
                }
            }
        }

    override suspend fun restoreBackupFile(uriString: String): ResultCode =
        withContext(Dispatchers.IO) {
            var inputStream: InputStream? = null
            var reader: BufferedReader? = null

            try {
                val uri = Uri.parse(uriString)
                inputStream = contentResolver.openInputStream(uri)
                reader = inputStream?.let(::InputStreamReader)?.let(::BufferedReader)

                var line: String
                var parts: List<String>

                // Check file identification
                line = reader?.readLine().orEmpty()
                if (line != BACKUP_IDENTIFICATION) return@withContext ResultCode.ERROR

                recordTypeRepo.clear()
                recordRepo.clear()
                categoryRepo.clear()
                recordTypeCategoryRepo.clear()
                recordTagRepo.clear()
                recordToRecordTagRepo.clear()
                activityFilterRepo.clear()

                // Read data
                while (reader?.readLine()?.also { line = it } != null) {
                    parts = line.split("\t")
                    when (parts[0]) {
                        ROW_RECORD_TYPE -> {
                            recordTypeFromBackupString(parts).let {
                                recordTypeRepo.add(it)
                            }
                        }
                        ROW_RECORD -> {
                            recordFromBackupString(parts).let { (record, recordToRecordTag) ->
                                recordRepo.add(record)
                                if (recordToRecordTag != null) {
                                    recordToRecordTagRepo.add(recordToRecordTag)
                                }
                            }
                        }
                        ROW_CATEGORY -> {
                            categoryFromBackupString(parts).let {
                                categoryRepo.add(it)
                            }
                        }
                        ROW_TYPE_CATEGORY -> {
                            typeCategoryFromBackupString(parts).let {
                                recordTypeCategoryRepo.add(it)
                            }
                        }
                        ROW_RECORD_TAG -> {
                            recordTagFromBackupString(parts).let {
                                recordTagRepo.add(it)
                            }
                        }
                        ROW_RECORD_TO_RECORD_TAG -> {
                            recordToRecordTagFromBackupString(parts).let {
                                recordToRecordTagRepo.add(it)
                            }
                        }
                        ROW_ACTIVITY_FILTER -> {
                            activityFilterFromBackupString(parts).let {
                                activityFilterRepo.add(it)
                            }
                        }
                    }
                }
                ResultCode.SUCCESS
            } catch (e: Exception) {
                Timber.e(e)
                ResultCode.ERROR
            } finally {
                try {
                    inputStream?.close()
                    reader?.close()
                } catch (e: IOException) {
                    // Do nothing
                }
            }
        }

    private fun toBackupString(recordType: RecordType): String {
        return String.format(
            "$ROW_RECORD_TYPE\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n",
            recordType.id.toString(),
            recordType.name.clean(),
            recordType.icon,
            recordType.color.colorId.toString(),
            (if (recordType.hidden) 1 else 0).toString(),
            recordType.goalTime.toString(),
            recordType.color.colorInt
        )
    }

    private fun toBackupString(record: Record): String {
        return String.format(
            "$ROW_RECORD\t%s\t%s\t%s\t%s\t%s\t%s\n",
            record.id.toString(),
            record.typeId.toString(),
            record.timeStarted.toString(),
            record.timeEnded.toString(),
            record.comment.cleanTabs().replaceNewline(),
            "", // record tag id is removed from record dbo
        )
    }

    private fun toBackupString(category: Category): String {
        return String.format(
            "$ROW_CATEGORY\t%s\t%s\t%s\t%s\n",
            category.id.toString(),
            category.name.clean(),
            category.color.colorId.toString(),
            category.color.colorInt,
        )
    }

    private fun toBackupString(recordTypeCategory: RecordTypeCategory): String {
        return String.format(
            "$ROW_TYPE_CATEGORY\t%s\t%s\n",
            recordTypeCategory.recordTypeId.toString(),
            recordTypeCategory.categoryId.toString()
        )
    }

    private fun toBackupString(recordTag: RecordTag): String {
        return String.format(
            "$ROW_RECORD_TAG\t%s\t%s\t%s\t%s\t%s\t%s\n",
            recordTag.id.toString(),
            recordTag.typeId.toString(),
            recordTag.name.clean(),
            (if (recordTag.archived) 1 else 0).toString(),
            recordTag.color.colorId.toString(),
            recordTag.color.colorInt,
        )
    }

    private fun toBackupString(recordToRecordTag: RecordToRecordTag): String {
        return String.format(
            "$ROW_RECORD_TO_RECORD_TAG\t%s\t%s\n",
            recordToRecordTag.recordId.toString(),
            recordToRecordTag.recordTagId.toString()
        )
    }

    private fun toBackupString(activityFilter: ActivityFilter): String {
        val typeString = when (activityFilter.type) {
            is ActivityFilter.Type.Activity -> 0L
            is ActivityFilter.Type.Category -> 1L
        }.toString()

        return String.format(
            "$ROW_ACTIVITY_FILTER\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n",
            activityFilter.id.toString(),
            activityFilter.selectedIds.joinToString(separator = ","),
            typeString,
            activityFilter.name.clean(),
            activityFilter.color.colorId.toString(),
            activityFilter.color.colorInt,
            (if (activityFilter.selected) 1 else 0).toString(),
        )
    }

    private fun recordTypeFromBackupString(parts: List<String>): RecordType {
        return RecordType(
            id = parts.getOrNull(1)?.toLongOrNull().orZero(),
            name = parts.getOrNull(2).orEmpty(),
            icon = parts.getOrNull(3).orEmpty(),
            color = AppColor(
                colorId = parts.getOrNull(4)?.toIntOrNull().orZero(),
                colorInt = parts.getOrNull(7).orEmpty(),
            ),
            hidden = parts.getOrNull(5)?.toIntOrNull() == 1,
            goalTime = parts.getOrNull(6)?.toLongOrNull().orZero()
        )
    }

    private fun recordFromBackupString(parts: List<String>): Pair<Record, RecordToRecordTag?> {
        val recordId = parts.getOrNull(1)?.toLongOrNull().orZero()
        // tag id is removed from record dbo, need to support old backup files.
        val tagId = parts.getOrNull(6)?.toLongOrNull().orZero()
        // replaces all return symbols to newlines by creating a temporary mutable list to replace return symbols from
        val mutableComment = parts.toMutableList()
        for (i in mutableComment.indices) {
            mutableComment[i] = mutableComment[i].replace("␤", "\n")
        }

        return Record(
            id = recordId,
            typeId = parts.getOrNull(2)?.toLongOrNull() ?: 1L,
            timeStarted = parts.getOrNull(3)?.toLongOrNull().orZero(),
            timeEnded = parts.getOrNull(4)?.toLongOrNull().orZero(),
            comment = mutableComment.toList().getOrNull(5).orEmpty(),
            // parts[6] - tag id is removed from record dbo.
        ) to RecordToRecordTag(
            recordId = recordId,
            recordTagId = tagId
        ).takeUnless { tagId == 0L }
    }

    private fun categoryFromBackupString(parts: List<String>): Category {
        return Category(
            id = parts.getOrNull(1)?.toLongOrNull().orZero(),
            name = parts.getOrNull(2).orEmpty(),
            color = AppColor(
                colorId = parts.getOrNull(3)?.toIntOrNull().orZero(),
                colorInt = parts.getOrNull(4).orEmpty(),
            )
        )
    }

    private fun typeCategoryFromBackupString(parts: List<String>): RecordTypeCategory {
        return RecordTypeCategory(
            recordTypeId = parts.getOrNull(1)?.toLongOrNull().orZero(),
            categoryId = parts.getOrNull(2)?.toLongOrNull().orZero()
        )
    }

    private fun recordTagFromBackupString(parts: List<String>): RecordTag {
        return RecordTag(
            id = parts.getOrNull(1)?.toLongOrNull().orZero(),
            typeId = parts.getOrNull(2)?.toLongOrNull().orZero(),
            name = parts.getOrNull(3).orEmpty(),
            archived = parts.getOrNull(4)?.toIntOrNull() == 1,
            color = AppColor(
                colorId = parts.getOrNull(5)?.toIntOrNull().orZero(),
                colorInt = parts.getOrNull(6).orEmpty(),
            )
        )
    }

    private fun recordToRecordTagFromBackupString(parts: List<String>): RecordToRecordTag {
        return RecordToRecordTag(
            recordId = parts.getOrNull(1)?.toLongOrNull().orZero(),
            recordTagId = parts.getOrNull(2)?.toLongOrNull().orZero()
        )
    }

    private fun activityFilterFromBackupString(parts: List<String>): ActivityFilter {
        return ActivityFilter(
            id = parts.getOrNull(1)?.toLongOrNull().orZero(),
            selectedIds = parts.getOrNull(2)?.split(",")
                ?.mapNotNull { it.toLongOrNull() }.orEmpty(),
            type = parts.getOrNull(3)?.toLongOrNull()?.let {
                when (it) {
                    0L -> ActivityFilter.Type.Activity
                    1L -> ActivityFilter.Type.Category
                    else -> ActivityFilter.Type.Activity
                }
            } ?: ActivityFilter.Type.Activity,
            name = parts.getOrNull(4).orEmpty(),
            color = AppColor(
                colorId = parts.getOrNull(5)?.toIntOrNull().orZero(),
                colorInt = parts.getOrNull(6).orEmpty(),
            ),
            selected = parts.getOrNull(7)?.toIntOrNull() == 1,
        )
    }

    private fun String.clean() =
        cleanTabs().cleanNewline()

    private fun String.cleanTabs() =
        replace("\t", " ")

    private fun String.cleanNewline() =
        replace("\n", " ")

    private fun String.replaceNewline() =
        replace("\n", "␤")

    companion object {
        private const val BACKUP_IDENTIFICATION = "app simple time tracker"
        private const val ROW_RECORD_TYPE = "recordType"
        private const val ROW_RECORD = "record"
        private const val ROW_CATEGORY = "category"
        private const val ROW_TYPE_CATEGORY = "typeCategory"
        private const val ROW_RECORD_TAG = "recordTag"
        private const val ROW_RECORD_TO_RECORD_TAG = "recordToRecordTag"
        private const val ROW_ACTIVITY_FILTER = "activityFilter"
    }
}
