package com.example.obsidiankeep

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.example.obsidiankeep.data.Note
import com.example.obsidiankeep.ui.theme.NoteColors
import java.util.Calendar
import java.util.Locale

enum class CalendarViewMode { WEEK, MONTH, YEAR, DAY }

/**
 * Календарь заметок-напоминаний в стиле Google Calendar.
 *
 * Четыре режима:
 *  - WEEK: текущая неделя (7 дней) с количеством напоминаний
 *  - MONTH: сетка месяца 7×N с точками-индикаторами
 *  - YEAR: 12 месяцев сеткой, с количеством напоминаний в каждом
 *  - DAY: один день с детальным списком событий + создание/удаление
 *
 * Под списком событий — кнопка «+ Заметка» (создаёт заметку с напоминанием на выбранный день).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    notes: List<Note>,
    onNoteClick: (String) -> Unit,
    onBack: () -> Unit,
    onCreateNoteForDay: (Long) -> Unit,
    onDeleteNote: (String) -> Unit
) {
    var viewMode by remember { mutableStateOf(CalendarViewMode.MONTH) }
    // displayedCal — храним как state; при навигации создаём НОВЫЙ Calendar (через clone+add),
    // иначе Compose не видит изменение (Calendar мутируемый).
    var displayedCal by remember {
        mutableStateOf(Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        })
    }
    var selectedDay by remember {
        mutableStateOf(Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        })
    }

    val monthNames = listOf(
        R.string.calendar_months_jan, R.string.calendar_months_feb, R.string.calendar_months_mar,
        R.string.calendar_months_apr, R.string.calendar_months_may, R.string.calendar_months_jun,
        R.string.calendar_months_jul, R.string.calendar_months_aug, R.string.calendar_months_sep,
        R.string.calendar_months_oct, R.string.calendar_months_nov, R.string.calendar_months_dec
    )
    val dowNames = listOf(
        R.string.calendar_mon, R.string.calendar_tue, R.string.calendar_wed, R.string.calendar_thu,
        R.string.calendar_fri, R.string.calendar_sat, R.string.calendar_sun
    )
    val noEventsText = stringResource(R.string.calendar_no_events)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.calendar_title), color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("←", color = Color.White, fontSize = 28.sp, modifier = Modifier.offset(y = (-7).dp))
                        }
                    }
                },
                actions = {
                    TextButton(onClick = {
                        val now = Calendar.getInstance().apply {
                            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                        }
                        displayedCal = now.clone() as Calendar
                        selectedDay = now.clone() as Calendar
                    }) { Text(stringResource(R.string.calendar_today), color = Color(0xFFBB86FC), fontSize = 12.sp) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF1A1A1A))
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF121212))
                .padding(innerPadding)
        ) {
            // Переключатель режимов
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                listOf(
                    CalendarViewMode.WEEK to R.string.calendar_view_week,
                    CalendarViewMode.MONTH to R.string.calendar_view_month,
                    CalendarViewMode.YEAR to R.string.calendar_view_year,
                    CalendarViewMode.DAY to R.string.calendar_view_day
                ).forEach { (mode, label) ->
                    FilterChip(
                        selected = viewMode == mode,
                        onClick = { viewMode = mode },
                        label = { Text(stringResource(label), color = Color.White, fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Color(0xFFBB86FC)),
                        modifier = Modifier.padding(horizontal = 2.dp)
                    )
                }
            }

            // Навигация по периоду: ‹ Название › (создаём новый Calendar при навигации — иначе Compose не видит)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = {
                    val newCal = (displayedCal.clone() as Calendar)
                    navigatePeriod(newCal, viewMode, -1)
                    displayedCal = newCal
                }) {
                    Text("‹", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                }
                Text(
                    text = formatPeriodTitle(displayedCal, viewMode, monthNames),
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center
                )
                IconButton(onClick = {
                    val newCal = (displayedCal.clone() as Calendar)
                    navigatePeriod(newCal, viewMode, +1)
                    displayedCal = newCal
                }) {
                    Text("›", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                }
            }

            when (viewMode) {
                CalendarViewMode.WEEK -> WeekView(displayedCal, selectedDay, notes, dowNames) { day ->
                    selectedDay = day
                }
                CalendarViewMode.MONTH -> MonthView(displayedCal, selectedDay, notes, dowNames) { day ->
                    selectedDay = day
                }
                CalendarViewMode.YEAR -> YearView(displayedCal, notes, monthNames) { month ->
                    val newCal = (displayedCal.clone() as Calendar)
                    newCal.set(Calendar.MONTH, month)
                    displayedCal = newCal
                    viewMode = CalendarViewMode.MONTH
                }
                CalendarViewMode.DAY -> {
                    // В режиме DAY навигация стрелками меняет selectedDay
                    // (а displayedCal синхронизируем с ним)
                    LaunchedEffect(selectedDay) {
                        displayedCal = (selectedDay.clone() as Calendar)
                    }
                }
            }

            Divider(color = Color(0xFF333333), modifier = Modifier.padding(vertical = 8.dp))

            // Список напоминаний в выбранный день
            val dayStart = (selectedDay.clone() as Calendar).apply {
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }
            val dayEnd = (dayStart.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, 1) }
            val dayEvents = remember(notes, selectedDay) {
                notes.filter { n ->
                    val r = n.reminderAt ?: return@filter false
                    r >= dayStart.timeInMillis && r < dayEnd.timeInMillis
                }.sortedBy { it.reminderAt }
            }

            Text(
                text = formatDay(selectedDay) + " · " + stringResource(R.string.calendar_event_count, dayEvents.size),
                color = Color(0xFFBB86FC),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )

            if (dayEvents.isEmpty()) {
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(noEventsText, color = Color.Gray, fontSize = 13.sp)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(dayEvents, key = { it.id }) { note ->
                        CalendarEventRow(
                            note = note,
                            onClick = { onNoteClick(note.id) },
                            onDelete = { onDeleteNote(note.id) }
                        )
                    }
                }
            }

            // Кнопка создания заметки с напоминанием на выбранный день (12:00 по умолчанию)
            FloatingActionButton(
                onClick = {
                    val trigger = (selectedDay.clone() as Calendar).apply {
                        set(Calendar.HOUR_OF_DAY, 12); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                    }
                    onCreateNoteForDay(trigger.timeInMillis)
                },
                containerColor = Color(0xFFBB86FC),
                modifier = Modifier
                    .align(Alignment.End)
                    .padding(24.dp)
            ) {
                Text(stringResource(R.string.calendar_add_note), color = Color.Black, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp))
            }
        }
    }
}

@Composable
private fun WeekView(
    displayedCal: Calendar,
    selectedDay: Calendar,
    notes: List<Note>,
    dowNames: List<Int>,
    onDayClick: (Calendar) -> Unit
) {
    val weekStart = (displayedCal.clone() as Calendar).apply {
        set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }
    val today = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        for (i in 0 until 7) {
            val day = (weekStart.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, i) }
            val isToday = sameDay(day, today)
            val isSelected = sameDay(day, selectedDay)
            val dayEvents = countEventsOnDay(notes, day)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(2.dp)
                    .clickable { onDayClick(day) },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(stringResource(dowNames[i]), color = Color.Gray, fontSize = 10.sp)
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(if (isSelected) Color(0xFFBB86FC) else Color.Transparent, CircleShape)
                        .border(
                            width = if (isToday) 2.dp else 0.dp,
                            color = if (isToday) Color(0xFFBB86FC) else Color.Transparent,
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = day.get(Calendar.DAY_OF_MONTH).toString(),
                        color = if (isSelected) Color.Black else Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                if (dayEvents > 0) {
                    Text(
                        text = stringResource(R.string.calendar_event_count, dayEvents),
                        color = Color(0xFF43A047),
                        fontSize = 9.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun MonthView(
    displayedCal: Calendar,
    selectedDay: Calendar,
    notes: List<Note>,
    dowNames: List<Int>,
    onDayClick: (Calendar) -> Unit
) {
    val firstOfMonth = (displayedCal.clone() as Calendar).apply {
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }
    val daysInMonth = displayedCal.getActualMaximum(Calendar.DAY_OF_MONTH)
    val firstDayOfWeek = (firstOfMonth.get(Calendar.DAY_OF_WEEK) + 5) % 7
    val today = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }

    Column(modifier = Modifier.padding(horizontal = 8.dp)) {
        Row(modifier = Modifier.fillMaxWidth()) {
            dowNames.forEach { dow ->
                Text(
                    text = stringResource(dow),
                    color = Color.Gray,
                    fontSize = 10.sp,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        var dayCounter = 1
        val totalCells = ((firstDayOfWeek + daysInMonth + 6) / 7) * 7
        for (weekRow in 0 until totalCells / 7) {
            Row(modifier = Modifier.fillMaxWidth()) {
                for (col in 0 until 7) {
                    val cellIdx = weekRow * 7 + col
                    if (cellIdx < firstDayOfWeek || dayCounter > daysInMonth) {
                        Box(modifier = Modifier.weight(1f).padding(2.dp)) {}
                    } else {
                        val day = (firstOfMonth.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, dayCounter - 1) }
                        val isToday = sameDay(day, today)
                        val isSelected = sameDay(day, selectedDay)
                        val dayEvents = countEventsOnDay(notes, day)
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(2.dp)
                                .height(48.dp)
                                .background(
                                    if (isSelected) Color(0xFFBB86FC).copy(alpha = 0.3f) else Color(0xFF1E1E1E),
                                    RoundedCornerShape(4.dp)
                                )
                                .border(
                                    width = if (isToday) 1.dp else 0.dp,
                                    color = if (isToday) Color(0xFFBB86FC) else Color.Transparent,
                                    shape = RoundedCornerShape(4.dp)
                                )
                                .clickable { onDayClick(day) },
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = dayCounter.toString(),
                                color = if (isSelected) Color.White else if (isToday) Color(0xFFBB86FC) else Color.White,
                                fontSize = 11.sp,
                                fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                            if (dayEvents > 0) {
                                Box(
                                    modifier = Modifier
                                        .padding(top = 2.dp)
                                        .size(4.dp)
                                        .background(Color(0xFF43A047), CircleShape)
                                )
                            }
                        }
                        dayCounter++
                    }
                }
            }
        }
    }
}

@Composable
private fun YearView(
    displayedCal: Calendar,
    notes: List<Note>,
    monthNames: List<Int>,
    onMonthClick: (Int) -> Unit
) {
    val year = displayedCal.get(Calendar.YEAR)
    val monthEventsCount = IntArray(12)
    for (month in 0 until 12) {
        val monthStart = Calendar.getInstance().apply {
            set(year, month, 1, 0, 0, 0); set(Calendar.MILLISECOND, 0)
        }
        val monthEnd = (monthStart.clone() as Calendar).apply { add(Calendar.MONTH, 1) }
        monthEventsCount[month] = notes.count { n ->
            val r = n.reminderAt ?: return@count false
            r >= monthStart.timeInMillis && r < monthEnd.timeInMillis
        }
    }
    val today = Calendar.getInstance()

    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(12) { month ->
            val isCurrentMonth = year == today.get(Calendar.YEAR) && month == today.get(Calendar.MONTH)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(70.dp)
                    .background(Color(0xFF1E1E1E), RoundedCornerShape(8.dp))
                    .border(
                        width = if (isCurrentMonth) 2.dp else 0.dp,
                        color = if (isCurrentMonth) Color(0xFFBB86FC) else Color.Transparent,
                        shape = RoundedCornerShape(8.dp)
                    )
                    .clickable { onMonthClick(month) }
                    .padding(8.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(stringResource(monthNames[month]), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                if (monthEventsCount[month] > 0) {
                    Text(
                        text = stringResource(R.string.calendar_event_count, monthEventsCount[month]),
                        color = Color(0xFF43A047),
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun CalendarEventRow(
    note: Note,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val reminderTime = remember(note.reminderAt) {
        val cal = Calendar.getInstance().apply { timeInMillis = note.reminderAt ?: 0L }
        String.format("%02d:%02d", cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF1E1E1E), RoundedCornerShape(8.dp))
            .border(1.dp, Color(0xFF333333), RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier
            .size(12.dp)
            .background(Color(note.color), RoundedCornerShape(3.dp)))
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = note.title.ifEmpty { stringResource(R.string.editor_no_title) },
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text("🔔 $reminderTime", color = Color(0xFFFB8C00), fontSize = 11.sp)
        }
        if (note.repeatRule != null && note.repeatRule != "none") {
            Text("🔁", fontSize = 14.sp)
        }
        // Кнопка удаления
        FilledIconButton(
            onClick = onDelete,
            colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color(0xFFE53935)),
            modifier = Modifier.size(32.dp)
        ) { Text("✕", color = Color.White, fontSize = 14.sp) }
    }
}

// === Helpers ===

private fun navigatePeriod(cal: Calendar, mode: CalendarViewMode, direction: Int) {
    when (mode) {
        CalendarViewMode.WEEK -> cal.add(Calendar.WEEK_OF_YEAR, direction)
        CalendarViewMode.MONTH -> cal.add(Calendar.MONTH, direction)
        CalendarViewMode.YEAR -> cal.add(Calendar.YEAR, direction)
        CalendarViewMode.DAY -> cal.add(Calendar.DAY_OF_MONTH, direction)
    }
}

private fun formatPeriodTitle(cal: Calendar, mode: CalendarViewMode, monthNames: List<Int>): String {
    return when (mode) {
        CalendarViewMode.WEEK -> {
            val weekStart = (cal.clone() as Calendar).apply { set(Calendar.DAY_OF_WEEK, Calendar.MONDAY) }
            val weekEnd = (weekStart.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, 6) }
            val fmt = java.text.SimpleDateFormat("d MMM", Locale.getDefault())
            "${fmt.format(weekStart.time)} — ${fmt.format(weekEnd.time)}"
        }
        CalendarViewMode.MONTH -> {
            val fmt = java.text.SimpleDateFormat("LLLL yyyy", Locale.getDefault())
            fmt.format(cal.time).replaceFirstChar { it.uppercase() }
        }
        CalendarViewMode.YEAR -> cal.get(Calendar.YEAR).toString()
        CalendarViewMode.DAY -> formatDay(cal)
    }
}

private fun formatDay(cal: Calendar): String {
    val fmt = java.text.SimpleDateFormat("d MMMM yyyy", Locale.getDefault())
    return fmt.format(cal.time)
}

private fun sameDay(a: Calendar, b: Calendar): Boolean =
    a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)

private fun countEventsOnDay(notes: List<Note>, day: Calendar): Int {
    val dayStart = (day.clone() as Calendar).apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }
    val dayEnd = (dayStart.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, 1) }
    return notes.count { n ->
        val r = n.reminderAt ?: return@count false
        r >= dayStart.timeInMillis && r < dayEnd.timeInMillis
    }
}
