package com.example.util.simpletimetracker.feature_change_record.mapper

import com.example.util.simpletimetracker.core.mapper.ColorMapper
import com.example.util.simpletimetracker.core.mapper.IconMapper
import com.example.util.simpletimetracker.core.mapper.TimeMapper
import com.example.util.simpletimetracker.domain.extension.getFullName
import com.example.util.simpletimetracker.domain.model.Record
import com.example.util.simpletimetracker.domain.model.RecordTag
import com.example.util.simpletimetracker.domain.model.RecordType
import com.example.util.simpletimetracker.feature_change_record.viewData.ChangeRecordViewData
import javax.inject.Inject

class ChangeRecordViewDataMapper @Inject constructor(
    private val iconMapper: IconMapper,
    private val colorMapper: ColorMapper,
    private val timeMapper: TimeMapper,
) {

    fun map(
        record: Record?,
        recordType: RecordType?,
        recordTags: List<RecordTag>,
        isDarkTheme: Boolean,
        useMilitaryTime: Boolean,
        useProportionalMinutes: Boolean,
    ): ChangeRecordViewData {
        return ChangeRecordViewData(
            name = recordType?.name.orEmpty(),
            tagName = recordTags
                .getFullName(),
            timeStarted = record?.timeStarted
                ?.let { timeMapper.formatTime(it, useMilitaryTime) }
                .orEmpty(),
            timeFinished = record?.timeEnded
                ?.let { timeMapper.formatTime(it, useMilitaryTime) }
                .orEmpty(),
            dateTimeStarted = record?.timeStarted
                ?.let { timeMapper.formatDateTime(it, useMilitaryTime) }
                .orEmpty(),
            dateTimeFinished = record?.timeEnded
                ?.let { timeMapper.formatDateTime(it, useMilitaryTime) }
                .orEmpty(),
            duration = record
                ?.let { it.timeEnded - it.timeStarted }
                ?.let { timeMapper.formatInterval(it, useProportionalMinutes) }
                .orEmpty(),
            iconId = recordType?.icon.orEmpty()
                .let(iconMapper::mapIcon),
            color = recordType?.color
                ?.let { colorMapper.mapToColorInt(it, isDarkTheme) }
                ?: colorMapper.toUntrackedColor(isDarkTheme),
            comment = record?.comment
                .orEmpty()
        )
    }
}