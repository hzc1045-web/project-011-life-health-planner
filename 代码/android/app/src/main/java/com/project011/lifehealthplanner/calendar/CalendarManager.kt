package com.project011.lifehealthplanner.calendar

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import com.project011.lifehealthplanner.data.local.CalendarLinkEntity
import com.project011.lifehealthplanner.data.local.PlanItemEntity
import com.project011.lifehealthplanner.data.remote.BusyBlockDto
import java.time.Instant

class CalendarManager(private val context: Context) {
    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED

    fun readBusyBlocks(start: Instant, end: Instant): List<BusyBlockDto> {
        if (!hasPermission()) return emptyList()
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, start.toEpochMilli())
            ContentUris.appendId(it, end.toEpochMilli())
        }.build()
        val projection = arrayOf(
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY,
        )
        return context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    val eventStart = cursor.getLong(0)
                    val eventEnd = cursor.getLong(1)
                    if (eventEnd > eventStart) {
                        add(
                            BusyBlockDto(
                                startAt = Instant.ofEpochMilli(eventStart).toString(),
                                endAt = Instant.ofEpochMilli(eventEnd).toString(),
                                label = if (cursor.getInt(2) == 1) "全天忙碌" else "忙碌",
                            ),
                        )
                    }
                }
            }
        } ?: emptyList()
    }

    fun insertPlanItem(item: PlanItemEntity): CalendarLinkEntity? {
        if (!hasPermission()) return null
        val calendarId = findOrCreateAppCalendar() ?: return null
        val values = ContentValues().apply {
            put(CalendarContract.Events.DTSTART, item.startAt)
            put(CalendarContract.Events.DTEND, item.endAt)
            put(CalendarContract.Events.TITLE, item.title)
            put(CalendarContract.Events.DESCRIPTION, item.description)
            put(CalendarContract.Events.CALENDAR_ID, calendarId)
            put(CalendarContract.Events.EVENT_TIMEZONE, java.util.TimeZone.getDefault().id)
        }
        val uri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values) ?: return null
        return CalendarLinkEntity(item.id, ContentUris.parseId(uri), calendarId)
    }

    fun deleteOwnedEvent(link: CalendarLinkEntity): Boolean {
        if (!hasPermission()) return false
        val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, link.eventId)
        val where = "${CalendarContract.Events.CALENDAR_ID} = ?"
        return context.contentResolver.delete(uri, where, arrayOf(link.calendarId.toString())) > 0
    }

    private fun findOrCreateAppCalendar(): Long? {
        val projection = arrayOf(CalendarContract.Calendars._ID)
        val selection =
            "${CalendarContract.Calendars.ACCOUNT_NAME} = ? AND ${CalendarContract.Calendars.ACCOUNT_TYPE} = ?"
        val arguments = arrayOf(ACCOUNT_NAME, CalendarContract.ACCOUNT_TYPE_LOCAL)
        context.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            projection,
            selection,
            arguments,
            null,
        )?.use { cursor -> if (cursor.moveToFirst()) return cursor.getLong(0) }

        val values = ContentValues().apply {
            put(CalendarContract.Calendars.ACCOUNT_NAME, ACCOUNT_NAME)
            put(CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
            put(CalendarContract.Calendars.NAME, "life_health_planner")
            put(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, "人生健康规划助手")
            put(CalendarContract.Calendars.CALENDAR_COLOR, 0xFF176B5B.toInt())
            put(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL, CalendarContract.Calendars.CAL_ACCESS_OWNER)
            put(CalendarContract.Calendars.OWNER_ACCOUNT, ACCOUNT_NAME)
            put(CalendarContract.Calendars.VISIBLE, 1)
            put(CalendarContract.Calendars.SYNC_EVENTS, 1)
        }
        val uri = CalendarContract.Calendars.CONTENT_URI.buildUpon()
            .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
            .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, ACCOUNT_NAME)
            .appendQueryParameter(
                CalendarContract.Calendars.ACCOUNT_TYPE,
                CalendarContract.ACCOUNT_TYPE_LOCAL,
            )
            .build()
        return context.contentResolver.insert(uri, values)?.let(ContentUris::parseId)
    }

    companion object {
        private const val ACCOUNT_NAME = "life-health-planner-local"
    }
}
