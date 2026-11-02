package com.lifeos.app

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.Manifest
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import com.batoulapps.adhan2.CalculationMethod
import com.batoulapps.adhan2.Coordinates
import com.batoulapps.adhan2.DateComponents
import com.batoulapps.adhan2.HighLatitudeRule
import com.batoulapps.adhan2.Madhab
import com.batoulapps.adhan2.PrayerTimes

class MainActivity : Activity() {
    private val bg = Color.rgb(16, 23, 19)
    private val panel = Color.rgb(27, 37, 31)
    private val panel2 = Color.rgb(35, 47, 40)
    private val text = Color.rgb(239, 243, 237)
    private val muted = Color.rgb(157, 173, 162)
    private val green = Color.rgb(126, 176, 145)
    private val prefs by lazy { getSharedPreferences("lifeos", Context.MODE_PRIVATE) }
    private var page = "day"
    private var matrixFilter = "Все"
    private var prayerLocation: Location? = null
    private var prayerMethod = "Мусульманская всемирная лига (MWL)"
    private var prayerMadhab = "Ханафи"
    private val locationPermissionRequest = 4102
    private lateinit var content: LinearLayout
    private lateinit var header: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        prayerMethod = prefs.getString("prayer_method", prayerMethod) ?: prayerMethod
        prayerMadhab = prefs.getString("prayer_madhab", prayerMadhab) ?: prayerMadhab
        render()
        if (hasLocationPermission()) refreshPrayerLocation()
        else requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), locationPermissionRequest)
    }

    private fun hasLocationPermission(): Boolean =
        checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun refreshPrayerLocation() {
        try {
            val lm = getSystemService(LOCATION_SERVICE) as LocationManager
            val candidates = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            var best: Location? = null
            candidates.forEach { provider ->
                if (lm.isProviderEnabled(provider)) {
                    val loc = try { lm.getLastKnownLocation(provider) } catch (_: SecurityException) { null }
                    if (loc != null && (best == null || loc.time > best!!.time)) best = loc
                }
            }
            if (best != null) {
                prayerLocation = best
                prefs.edit().putString("prayer_lat", best!!.latitude.toString())
                    .putString("prayer_lon", best!!.longitude.toString()).apply()
                if (page == "day") render()
            } else {
                val savedLat = prefs.getString("prayer_lat", null)?.toDoubleOrNull()
                val savedLon = prefs.getString("prayer_lon", null)?.toDoubleOrNull()
                if (savedLat != null && savedLon != null) {
                    prayerLocation = Location("saved").apply { latitude = savedLat; longitude = savedLon }
                    if (page == "day") render()
                }
                try {
                    val provider = if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) LocationManager.NETWORK_PROVIDER else LocationManager.GPS_PROVIDER
                    lm.requestSingleUpdate(provider, object : LocationListener {
                        override fun onLocationChanged(location: Location) {
                            prayerLocation = location
                            prefs.edit().putString("prayer_lat", location.latitude.toString())
                                .putString("prayer_lon", location.longitude.toString()).apply()
                            if (page == "day") render()
                        }
                        @Deprecated("Deprecated in Java") override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {}
                        override fun onProviderEnabled(provider: String) {}
                        override fun onProviderDisabled(provider: String) {}
                    }, mainLooper)
                } catch (_: Exception) { }
            }
        } catch (_: Exception) { }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == locationPermissionRequest && hasLocationPermission()) refreshPrayerLocation()
        else if (requestCode == locationPermissionRequest) render()
    }

    private fun prayerTimes(): Map<String, String>? {
        val loc = prayerLocation ?: return null
        return try {
            val now = java.util.Calendar.getInstance()
            val date = DateComponents(now.get(java.util.Calendar.YEAR), now.get(java.util.Calendar.MONTH) + 1, now.get(java.util.Calendar.DAY_OF_MONTH))
            val method = when (prayerMethod) {
                "Карачи" -> CalculationMethod.KARACHI
                "Турция" -> CalculationMethod.TURKEY
                "Египет" -> CalculationMethod.EGYPTIAN
                "Умм аль-Кура" -> CalculationMethod.UMM_AL_QURA
                else -> CalculationMethod.MUSLIM_WORLD_LEAGUE
            }
            val params = method.parameters.copy(
                madhab = if (prayerMadhab == "Ханафи") Madhab.HANAFI else Madhab.SHAFI,
                highLatitudeRule = HighLatitudeRule.MIDDLE_OF_THE_NIGHT
            )
            val times = PrayerTimes(Coordinates(loc.latitude, loc.longitude), date, params)
            val formatter = SimpleDateFormat("HH:mm", Locale.getDefault()).apply { timeZone = TimeZone.getDefault() }
            fun fmt(instant: kotlinx.datetime.Instant): String = formatter.format(Date(instant.toEpochMilliseconds()))
            mapOf("Фаджр" to fmt(times.fajr), "Зухр" to fmt(times.dhuhr), "Аср" to fmt(times.asr),
                "Магриб" to fmt(times.maghrib), "Иша" to fmt(times.isha))
        } catch (_: Exception) { null }
    }

    private fun prayerSettings() {
        val methods = listOf("Мусульманская всемирная лига (MWL)", "Карачи", "Турция", "Египет", "Умм аль-Кура")
        val methodSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, methods)
            setSelection(methods.indexOf(prayerMethod).coerceAtLeast(0))
        }
        val madhabSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, listOf("Ханафи", "Шафии / Малики / Ханбали"))
            setSelection(if (prayerMadhab == "Ханафи") 0 else 1)
        }
        val box = column().apply {
            setPadding(dp(20), dp(8), dp(20), dp(4))
            addView(label("Метод расчёта", 12f, muted)); addView(methodSpinner)
            addView(label("Расчёт Асра", 12f, muted)); addView(madhabSpinner)
            addView(label("Время вычисляется на устройстве по координатам и текущему часовому поясу. Метод местной мечети может отличаться.", 12f, muted))
        }
        AlertDialog.Builder(this).setTitle("Времена молитв").setView(box)
            .setNegativeButton("Отмена", null).setPositiveButton("Сохранить") { _, _ ->
                prayerMethod = methods[methodSpinner.selectedItemPosition]
                prayerMadhab = if (madhabSpinner.selectedItemPosition == 0) "Ханафи" else "Шафии / Малики / Ханбали"
                prefs.edit().putString("prayer_method", prayerMethod).putString("prayer_madhab", prayerMadhab).apply()
                render()
            }.show()
    }

    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()
    private fun shape(color: Int, radius: Int = 18): GradientDrawable =
        GradientDrawable().apply { setColor(color); cornerRadius = dp(radius).toFloat() }
    private fun label(s: String, size: Float = 14f, color: Int = text, bold: Boolean = false): TextView =
        TextView(this).apply {
            text = s; textSize = size; setTextColor(color)
            if (bold) setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(2), 0, dp(2))
        }
    private fun column(): LinearLayout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    private fun row(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
    }
    private fun card(): LinearLayout = column().apply {
        background = shape(panel); setPadding(dp(16), dp(14), dp(16), dp(14))
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) }
    }
    private fun addText(parent: LinearLayout, s: String, size: Float = 14f, color: Int = text, bold: Boolean = false) {
        parent.addView(label(s, size, color, bold))
    }
    private fun spacer(parent: LinearLayout, height: Int = 8) {
        parent.addView(View(this), LinearLayout.LayoutParams(1, dp(height)))
    }
    private fun button(s: String, action: () -> Unit): TextView = label(s, 14f, text, true).apply {
        gravity = Gravity.CENTER; setPadding(dp(14), dp(12), dp(14), dp(12)); background = shape(panel2, 14)
        setOnClickListener { action() }
    }
    private fun savedTasks(): JSONArray = try { JSONArray(prefs.getString("tasks", "[]")) } catch (_: Exception) { JSONArray() }
    private fun saveTasks(a: JSONArray) { prefs.edit().putString("tasks", a.toString()).apply() }
    private fun moneyRows(): JSONArray = try { JSONArray(prefs.getString("money", "[]")) } catch (_: Exception) { JSONArray() }
    private fun saveMoney(a: JSONArray) { prefs.edit().putString("money", a.toString()).apply() }
    private fun fmt(n: Long) = String.format(Locale("ru", "RU"), "%,d ₸", n).replace('\u00a0', ' ')

    private fun render() {
        val outer = column().apply { setBackgroundColor(bg) }
        header = column().apply { setPadding(dp(20), dp(18), dp(20), dp(10)) }
        val date = SimpleDateFormat("EEEE, d MMMM", Locale("ru")).format(Date())
        addText(header, "LIFEOS", 11f, green, true)
        spacer(header, 4)
        addText(header, when(page) { "day" -> "Мой день"; "matrix" -> "Матрица Эйзенхауэра"; "finance" -> "Финансы"; else -> "Цели и привычки" }, 28f, text, true)
        addText(header, date.replaceFirstChar { it.uppercase() }, 13f, muted)
        outer.addView(header)
        content = column().apply { setPadding(dp(20), dp(6), dp(20), dp(18)) }
        val scroll = ScrollView(this).apply { isFillViewport = false; addView(content) }
        outer.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        val nav = row().apply {
            setPadding(dp(12), dp(10), dp(12), dp(10)); background = shape(panel)
        }
        listOf("day" to "Сегодня", "matrix" to "Матрица", "finance" to "Финансы", "goals" to "Цели").forEach { (id, name) ->
            val b = label(name, 13f, if (page == id) green else muted, page == id).apply {
                gravity = Gravity.CENTER; setPadding(dp(8), dp(12), dp(8), dp(12))
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
                setOnClickListener { page = id; render() }
            }
            nav.addView(b)
        }
        outer.addView(nav, LinearLayout.LayoutParams(-1, -2).apply {
            setMargins(dp(16), dp(4), dp(16), dp(10))
        })
        setContentView(outer)
        when(page) { "day" -> dayPage(); "matrix" -> matrixPage(); "finance" -> financePage(); else -> goalsPage() }
    }

    private fun dayPage() {
        val tasks = savedTasks()
        val done = (0 until tasks.length()).count { tasks.optJSONObject(it)?.optBoolean("done") == true }
        val summary = card()
        addText(summary, "ФОКУС ДНЯ", 11f, green, true)
        spacer(summary, 6)
        addText(summary, if (tasks.length() == 0) "Начни с одной важной задачи" else "$done из ${tasks.length()} задач выполнено", 19f, text, true)
        spacer(summary, 8)
        val progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = maxOf(1, tasks.length()); progress = done; progressTintList = android.content.res.ColorStateList.valueOf(green)
            progressBackgroundTintList = android.content.res.ColorStateList.valueOf(panel2)
        }
        summary.addView(progress, LinearLayout.LayoutParams(-1, dp(5)))
        spacer(summary, 10)
        summary.addView(button("+ Добавить задачу") { addTask() })
        content.addView(summary)

        val calculated = prayerTimes()
        val prayerNames = listOf("Фаджр", "Зухр", "Аср", "Магриб", "Иша")
        val descriptions = listOf(
            "Рассвет · настрой на день",
            "Полдень · пауза и переоценка приоритетов",
            "После полудня · завершение главных задач",
            "Закат · семья и личное время",
            "Вечер · итоги дня и планирование"
        )
        val locationCard = card()
        addText(locationCard, "ВРЕМЕНА МОЛИТВ", 11f, green, true)
        spacer(locationCard, 4)
        addText(locationCard, if (calculated != null) "Расчёт по текущему местоположению" else "Нужно разрешение на местоположение", 14f, text, true)
        addText(locationCard, if (calculated != null) "$prayerMethod · мазхаб: $prayerMadhab" else "Разреши доступ к местоположению, чтобы рассчитать время автоматически.", 12f, muted)
        spacer(locationCard, 8)
        locationCard.addView(button(if (calculated == null) "Определить местоположение" else "Настроить расчёт") {
            if (!hasLocationPermission()) requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), locationPermissionRequest)
            else if (calculated == null) { refreshPrayerLocation(); render() } else prayerSettings()
        })
        content.addView(locationCard)

        prayerNames.forEachIndexed { index, prayer ->
            val c = card()
            val top = row()
            val marker = TextView(this).apply {
                text = "${index + 1}"; textSize = 13f; setTextColor(green); gravity = Gravity.CENTER
                background = shape(panel2, 30)
            }
            top.addView(marker, LinearLayout.LayoutParams(dp(34), dp(34)))
            val labels = column().apply { setPadding(dp(12), 0, 0, 0) }
            labels.addView(label(prayer, 17f, text, true))
            labels.addView(label(descriptions[index], 12f, muted))
            top.addView(labels, LinearLayout.LayoutParams(0, -2, 1f))
            val time = calculated?.get(prayer) ?: "--:--"
            top.addView(label(time, 19f, if (calculated != null) green else muted, true))
            c.addView(top)
            content.addView(c)
            if (index < prayerNames.size - 1) {
                val between = tasksForPrayer(index, tasks)
                between.forEach { taskIndex ->
                    val task = tasks.optJSONObject(taskIndex) ?: return@forEach
                    val tr = card()
                    val rr = row()
                    val cb = CheckBox(this).apply {
                        isChecked = task.optBoolean("done")
                        buttonTintList = android.content.res.ColorStateList.valueOf(green)
                        setOnCheckedChangeListener { _, checked ->
                            task.put("done", checked); saveTasks(tasks); render()
                        }
                    }
                    rr.addView(cb)
                    rr.addView(label(task.optString("title"), 14f, if(task.optBoolean("done")) muted else text, false),
                        LinearLayout.LayoutParams(0, -2, 1f))
                    tr.addView(rr)
                    content.addView(tr)
                }
            }
        }
        if (tasks.length() > 0) {
            spacer(content, 4)
            content.addView(button("＋ Новая задача") { addTask() })
        }
        addText(content, "Время рассчитывается офлайн по координатам устройства. Для религиозно значимых решений сверяй расписание с местной мечетью: метод расчёта и поправки могут отличаться.", 11f, muted)

    private fun tasksForPrayer(prayer: Int, tasks: JSONArray): List<Int> =
        (0 until tasks.length()).filter { (tasks.optJSONObject(it)?.optInt("slot", 0) ?: 0) == prayer }

    private fun addTask() {
        val input = EditText(this).apply { hint = "Название задачи"; setTextColor(text); setHintTextColor(muted) }
        val slot = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item,
                listOf("До Зухра", "До Асра", "До Магриба", "До Иша", "После Иша"))
        }
        val sphere = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item,
                listOf("Личное", "Работа / проекты"))
        }
        val important = CheckBox(this).apply { text = "Важно"; isChecked = true; setTextColor(text) }
        val urgent = CheckBox(this).apply { text = "Срочно"; setTextColor(text) }
        val box = column().apply {
            setPadding(dp(20), dp(8), dp(20), dp(4))
            addView(input); addView(label("Сфера", 12f, muted)); addView(sphere)
            addView(label("Время дня", 12f, muted)); addView(slot)
            addView(important); addView(urgent)
        }
        AlertDialog.Builder(this).setTitle("Новая задача").setView(box)
            .setNegativeButton("Отмена", null).setPositiveButton("Сохранить") { _, _ ->
                val title = input.text.toString().trim()
                if (title.isNotEmpty()) {
                    val tasks = savedTasks()
                    tasks.put(JSONObject().put("title", title).put("done", false).put("slot", slot.selectedItemPosition)
                        .put("sphere", sphere.selectedItem.toString()).put("important", important.isChecked)
                        .put("urgent", urgent.isChecked))
                    saveTasks(tasks); render()
                } else Toast.makeText(this, "Введи название задачи", Toast.LENGTH_SHORT).show()
            }.show()
    }

    private fun matrixPage() {
        val tasks = savedTasks()
        val filter = card()
        addText(filter, "СФЕРА", 11f, green, true)
        val filters = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item,
                listOf("Все", "Работа / проекты", "Личное"))
            setSelection(listOf("Все", "Работа / проекты", "Личное").indexOf(matrixFilter).coerceAtLeast(0))
            onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
                override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                    val next = parent?.getItemAtPosition(position)?.toString() ?: "Все"
                    if (next != matrixFilter) { matrixFilter = next; render() }
                }
            }
        }
        filter.addView(filters)
        spacer(filter, 8)
        filter.addView(button("+ Добавить задачу") { addTask() })
        content.addView(filter)

        val quadrants = listOf(
            Triple(true, true, "1. СДЕЛАТЬ", "Важно и срочно", "Приоритет: выполнить как можно скорее"),
            Triple(true, false, "2. ЗАПЛАНИРОВАТЬ", "Важно, но не срочно", "Выделить время в календаре"),
            Triple(false, true, "3. ДЕЛЕГИРОВАТЬ", "Срочно, но не важно", "Передать другому, если возможно"),
            Triple(false, false, "4. УДАЛИТЬ", "Не важно и не срочно", "Отказаться или сократить")
        )
        quadrants.forEach { q ->
            val important = q.first
            val urgent = q.second
            val matching = (0 until tasks.length()).mapNotNull { tasks.optJSONObject(it) }
                .filter { (matrixFilter == "Все" || it.optString("sphere", "Личное") == matrixFilter)
                    && it.optBoolean("important", true) == important
                    && it.optBoolean("urgent", false) == urgent
                    && !it.optBoolean("done", false) }
            val c = card()
            addText(c, q.third, 11f, green, true)
            addText(c, q.fourth, 17f, text, true)
            addText(c, q.fifth, 12f, muted)
            spacer(c, 8)
            if (matching.isEmpty()) addText(c, "Нет задач", 13f, muted)
            matching.forEach { task ->
                val line = row()
                val name = column()
                name.addView(label(task.optString("title"), 14f, text, true))
                name.addView(label(task.optString("sphere", "Личное"), 11f, muted))
                line.addView(name, LinearLayout.LayoutParams(0, -2, 1f))
                line.addView(button("✓") {
                    task.put("done", true); saveTasks(tasks); render()
                })
                c.addView(line)
                spacer(c, 6)
            }
            content.addView(c)
        }
        addText(content, "Правило: матрица определяет приоритет, а экран «Сегодня» — место задачи в течение дня. Завершённые задачи скрываются из матрицы.", 12f, muted)
    }

    private fun financePage() {
        val rows = moneyRows()
        var goods = 0L; var services = 0L; var other = 0L; var income = 0L
        for (i in 0 until rows.length()) {
            val o = rows.optJSONObject(i) ?: continue
            val amount = o.optLong("amount")
            if (o.optString("kind") == "income") income += amount
            else when(o.optString("type")) { "Товары" -> goods += amount; "Услуги" -> services += amount; else -> other += amount }
        }
        val overview = card()
        addText(overview, "ОБЗОР ФИНАНСОВ", 11f, green, true)
        spacer(overview, 8)
        addText(overview, "Расходы", 13f, muted)
        addText(overview, fmt(goods + services + other), 28f, text, true)
        spacer(overview, 10)
        addText(overview, "Доходы  ${fmt(income)}", 14f, green, true)
        addText(overview, "Товары  ${fmt(goods)}", 14f)
        addText(overview, "Услуги  ${fmt(services)}", 14f)
        addText(overview, "Прочее  ${fmt(other)}", 14f)
        content.addView(overview)

        val actions = row()
        listOf("＋ Расход" to { addMoney(false) }, "＋ Доход" to { addMoney(true) }).forEach { (title, action) ->
            actions.addView(button(title, action), LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(6) })
        }
        content.addView(actions, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(14) })

        val categories = listOf(
            "Здоровье" to listOf("Лекарства", "Массаж", "Врач"),
            "Питание" to listOf("Продукты", "Кафе", "Доставка еды"),
            "Транспорт" to listOf("Товары для транспорта", "Такси", "Общественный транспорт"),
            "Дом" to listOf("Мебель", "Ремонт и обслуживание"),
            "Образование" to listOf("Книги", "Курсы"),
            "Красота" to listOf("Косметика", "Уход и процедуры"),
            "Спорт" to listOf("Инвентарь", "Тренер и абонемент"),
            "Технологии" to listOf("Устройства", "Подписки")
        )
        val cat = card()
        addText(cat, "ЗЕРКАЛЬНЫЕ КАТЕГОРИИ", 11f, green, true)
        spacer(cat, 6)
        categories.forEach { (name, examples) ->
            addText(cat, name, 15f, text, true)
            addText(cat, examples.joinToString(" · "), 12f, muted)
            spacer(cat, 5)
        }
        content.addView(cat)
        addText(content, "ИСТОРИЯ ОПЕРАЦИЙ", 11f, green, true)
        if (rows.length() == 0) {
            val empty = card(); addText(empty, "Пока нет операций", 15f, text, true)
            addText(empty, "Добавь первый расход или доход.", 13f, muted); content.addView(empty)
        } else {
            for (i in rows.length() - 1 downTo 0) {
                val o = rows.optJSONObject(i) ?: continue
                val c = card()
                val r = row()
                val info = column()
                val name = o.optString("name", if(o.optString("kind") == "income") "Доход" else o.optString("type"))
                info.addView(label(name, 14f, text, true))
                info.addView(label("${o.optString("category")} · ${o.optString("type")}", 12f, muted))
                r.addView(info, LinearLayout.LayoutParams(0, -2, 1f))
                val sign = if (o.optString("kind") == "income") "+" else "−"
                r.addView(label("$sign${fmt(o.optLong("amount"))}", 14f, if(sign=="+") green else text, true))
                c.addView(r); content.addView(c)
            }
        }
    }

    private fun addMoney(isIncome: Boolean) {
        val name = EditText(this).apply { hint = if(isIncome) "Источник дохода" else "Что куплено или какая услуга"; setTextColor(text); setHintTextColor(muted) }
        val amount = EditText(this).apply { hint = "Сумма в тенге"; inputType = 2; setTextColor(text); setHintTextColor(muted) }
        val type = Spinner(this)
        val typeOptions = if (isIncome) listOf("Доход") else listOf("Товары", "Услуги", "Прочее")
        type.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, typeOptions)
        val cat = Spinner(this)
        val cats = listOf("Здоровье", "Питание", "Транспорт", "Дом", "Образование", "Красота", "Спорт", "Технологии", "Другое")
        cat.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, cats)
        val box = column().apply {
            setPadding(dp(20), dp(4), dp(20), dp(4))
            addView(name); addView(amount)
            if (!isIncome) { addText(this, "Тип расхода", 12f, muted); addView(type) }
            addText(this, "Категория", 12f, muted); addView(cat)
        }
        AlertDialog.Builder(this).setTitle(if(isIncome) "Новый доход" else "Новый расход").setView(box)
            .setNegativeButton("Отмена", null).setPositiveButton("Сохранить") { _, _ ->
                val n = name.text.toString().trim()
                val a = amount.text.toString().trim().toLongOrNull()
                if (n.isNotEmpty() && a != null && a > 0) {
                    val list = moneyRows()
                    list.put(JSONObject().put("name", n).put("amount", a)
                        .put("kind", if(isIncome) "income" else "expense")
                        .put("type", if(isIncome) "Доход" else type.selectedItem.toString())
                        .put("category", cat.selectedItem.toString())
                        .put("date", SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())))
                    saveMoney(list); render()
                } else Toast.makeText(this, "Укажи название и сумму больше нуля", Toast.LENGTH_SHORT).show()
            }.show()
    }

    private fun goalsPage() {
        val c = card()
        addText(c, "Система целей", 18f, text, true)
        addText(c, "Здесь будут долгосрочные цели, привычки и недельные обзоры. Сейчас доступен базовый каркас раздела.", 14f, muted)
        content.addView(c)
        addText(content, "ПРИНЦИПЫ", 11f, green, true)
        listOf("Одна система задач для работы и личной жизни", "Минимум действий для записи", "Финансовые операции сохраняются локально", "Единая тёмно-зелёная визуальная система").forEach {
            val item = card(); addText(item, "•  $it", 14f); content.addView(item)
        }
    }
}
