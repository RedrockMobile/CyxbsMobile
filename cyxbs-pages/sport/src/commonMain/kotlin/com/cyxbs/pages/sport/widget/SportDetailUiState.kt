package com.cyxbs.pages.sport.widget

import com.cyxbs.components.config.time.SchoolCalendar
import com.cyxbs.pages.sport.model.SportDetailBean
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

sealed interface SportDetailUiState {
    data object Loading : SportDetailUiState
    data class Holiday(
        val summary: SummaryUi
    ) : SportDetailUiState

    data class Content(
        val summary: SummaryUi,
        val records: List<SportRecordUi>,
    ) : SportDetailUiState

    data class Empty(
        val summary: SummaryUi
    ) : SportDetailUiState

    data object Error : SportDetailUiState
}

data class SportRecordUi(
    val date: String,
    val time: String,
    val spot: String,
    val type: String,
    val isAward: Boolean,
    val isValid: Boolean,
)

data class SummaryUi(
    val totalDone: String,
    val totalNeed: String,
    val runDone: String,
    val runNeed: String,
    val otherDone: String,
    val otherNeed: String,
    val award: String,
)

fun SportDetailBean.toDetailUiState(): SportDetailUiState {
    val summary = SummaryUi(
        totalDone = (runDone + otherDone).toString(),
        totalNeed = "/${runTotal + otherTotal}",
        runDone = runDone.toString(),
        runNeed = "/$runTotal",
        otherDone = otherDone.toString(),
        otherNeed = "/$otherTotal",
        award = award.toString(),
    )

    val records = item.orEmpty()
        .asReversed()
        .map { record ->
            SportRecordUi(
                date = record.date,
                time = record.time,
                spot = record.spot.normalizeSportSpot(),
                type = record.type,
                isAward = record.isAward,
                isValid = record.valid,
            )
        }

    val week = SchoolCalendar.getWeekOfTerm() ?: 22
    return if (week in 1..21) {
        if (records.isEmpty()) {
            SportDetailUiState.Empty(
                summary = summary,
            )
        } else {
            SportDetailUiState.Content(
                summary = summary,
                records = records,
            )
        }
    } else SportDetailUiState.Holiday(summary)
}

private fun String.normalizeSportSpot() = when (this) {
    "风雨操场（羽毛球馆）",
    "风雨操场（乒乓球馆）" -> "风雨操场"

    else -> this
}

fun currentTermText(): String {
    val currentDate = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
    val year = currentDate.year
    val month = currentDate.month.number
    val season = when (month) {
        1 -> "秋"
        in 2..7 -> "春"
        in 8..12 -> "秋"
        else -> ""
    }
    return "${year}年  $season"
}