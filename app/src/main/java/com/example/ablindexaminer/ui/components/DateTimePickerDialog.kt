package com.example.ablindexaminer.ui.components

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.widget.DatePicker
import android.widget.TimePicker
import java.util.*

/**
 * A helper class that shows a date picker followed by a time picker
 * and returns the selected date and time as a Date object
 */
class DateTimePickerDialog(
    private val context: Context,
    private val initialDate: Date = Date(),
    private val onDateTimeSelected: (Date) -> Unit
) {
    /**
     * Show the date picker dialog, followed by the time picker dialog
     */
    fun show() {
        // Create calendar with initial date
        val calendar = Calendar.getInstance().apply {
            time = initialDate
        }
        
        // Show date picker
        DatePickerDialog(
            context,
            { _: DatePicker, year: Int, month: Int, dayOfMonth: Int ->
                // Update calendar with selected date
                calendar.set(Calendar.YEAR, year)
                calendar.set(Calendar.MONTH, month)
                calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth)
                
                // Show time picker after date is selected
                showTimePicker(calendar)
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }
    
    /**
     * Show the time picker dialog
     */
    private fun showTimePicker(calendar: Calendar) {
        TimePickerDialog(
            context,
            { _: TimePicker, hourOfDay: Int, minute: Int ->
                // Update calendar with selected time
                calendar.set(Calendar.HOUR_OF_DAY, hourOfDay)
                calendar.set(Calendar.MINUTE, minute)
                calendar.set(Calendar.SECOND, 0)
                
                // Return the selected date and time
                onDateTimeSelected(calendar.time)
            },
            calendar.get(Calendar.HOUR_OF_DAY),
            calendar.get(Calendar.MINUTE),
            true // 24-hour format
        ).show()
    }
} 