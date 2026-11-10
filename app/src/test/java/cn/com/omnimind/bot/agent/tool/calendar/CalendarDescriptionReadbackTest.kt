package cn.com.omnimind.bot.agent

import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.CalendarContract
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, manifest = Config.NONE)
class CalendarDescriptionReadbackTest {
    @Test
    fun `event queries preserve multiline notes and return empty text for null notes`() {
        val notes = "Bring the draft\n会议室入口在二楼"
        ShadowContentResolver.registerProviderInternal(CalendarContract.AUTHORITY, object : ContentProvider() {
            override fun onCreate() = true
            override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor {
                val columns = requireNotNull(projection)
                val cursor = MatrixCursor(columns)
                if (uri.path.orEmpty().startsWith("/instances")) {
                    for ((id, description) in listOf(1L to notes, 2L to null)) {
                        val row = mapOf<String, Any?>(
                            CalendarContract.Instances.EVENT_ID to id,
                            CalendarContract.Instances.CALENDAR_ID to 1L,
                            CalendarContract.Instances.TITLE to "Owned test $id",
                            CalendarContract.Instances.BEGIN to Instant.parse("2028-02-29T10:00:00Z").toEpochMilli(),
                            CalendarContract.Instances.END to Instant.parse("2028-02-29T11:00:00Z").toEpochMilli(),
                            CalendarContract.Instances.EVENT_LOCATION to "Room B",
                            CalendarContract.Instances.ALL_DAY to 0,
                            CalendarContract.Instances.EVENT_TIMEZONE to "UTC",
                            CalendarContract.Instances.DESCRIPTION to description
                        )
                        cursor.addRow(columns.map { row[it] }.toTypedArray())
                    }
                }
                return cursor
            }
            override fun getType(uri: Uri): String? = null
            override fun insert(uri: Uri, values: ContentValues?): Uri? = error("Read-only test")
            override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = error("Read-only test")
            override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = error("Read-only test")
        })
        val result = AgentCalendarToolService(RuntimeEnvironment.getApplication()).listEvents(
            CalendarEventListRequest("1", "2028-02-29T00:00:00Z", "2028-03-01T00:00:00Z", "Owned test", null)
        )
        val items = result["items"] as List<*>
        assertEquals(2, items.size)
        assertEquals(notes, (items[0] as Map<*, *>)["description"])
        assertEquals("", (items[1] as Map<*, *>)["description"])
        assertEquals("2028-02-29T10:00:00Z", (items[0] as Map<*, *>)["startAt"])
    }
}
