package com.resync.app

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalLayoutDirection
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.random.Random

data class Person(val name: String, val gender: String = "خانم")
data class ScheduleRow(val person: Person, val date: LocalDate)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) { ResyncApp() } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun ResyncApp() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("resync", Context.MODE_PRIVATE) }
    var people by remember { mutableStateOf(loadPeople(prefs)) }
    var name by remember { mutableStateOf("") }
    var newGender by remember { mutableStateOf("خانم") }
    var darkOverride by remember { mutableStateOf<Boolean?>(null) }
    var query by remember { mutableStateOf("") }
    var searchOpen by remember { mutableStateOf(false) }
    var emoji by remember { mutableStateOf(prefs.getString("emoji", "💎") ?: "💎") }
    var startDate by remember { mutableStateOf(LocalDate.parse(prefs.getString("date", LocalDate.now().toString()))) }
    var previousSeed by remember { mutableLongStateOf(prefs.getLong("seed", 0L)) }
    var template by remember { mutableStateOf(prefs.getString("template", "رسمی") ?: "رسمی") }
    var rows by remember { mutableStateOf(emptyList<ScheduleRow>()) }
    var showList by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var bulkDialog by remember { mutableStateOf(false) }
    var editTarget by remember { mutableStateOf<Person?>(null) }
    var deleteTarget by remember { mutableStateOf<Person?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val clipboard = LocalClipboardManager.current

    fun persist() = prefs.edit().putString("people", people.joinToString("\\u001e") { "${it.name}\\u001f${it.gender}" }).putString("emoji", emoji).putString("date", startDate.toString()).putString("template", template).putLong("seed", previousSeed).apply()
    fun generate(reuse: Boolean) {
        if (people.isEmpty()) return
        val seed = if (reuse && previousSeed != 0L) previousSeed else System.currentTimeMillis()
        previousSeed = seed
        rows = people.shuffled(Random(seed)).mapIndexed { index, person -> ScheduleRow(person, startDate.plusDays(index.toLong())) }
        persist()
    }
    val output = formatOutput(rows, emoji, template)
    val filtered = people.filter { it.name.contains(query.trim(), true) }

    val dark = darkOverride ?: isSystemInDarkTheme()
    val colors = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else if (dark) darkColorScheme() else lightColorScheme()
    MaterialTheme(colorScheme = colors, shapes = Shapes(
      extraSmall = RoundedCornerShape(12.dp), small = RoundedCornerShape(16.dp),
      medium = RoundedCornerShape(24.dp), large = RoundedCornerShape(28.dp), extraLarge = RoundedCornerShape(32.dp)
    )) {
      Scaffold(
        topBar = { MediumTopAppBar(
          title = { Column { Text("Resync", fontWeight = FontWeight.Bold); Text("${people.size} نفر در فهرست", style = MaterialTheme.typography.labelMedium) } },
          actions = {
            IconButton(onClick = { searchOpen = !searchOpen; if (!searchOpen) query = "" }) { Icon(if (searchOpen) Icons.Default.Close else Icons.Default.Search, if (searchOpen) "بستن جست‌وجو" else "جست‌وجو") }
            IconButton(onClick = { darkOverride = !dark }) { Icon(if (dark) Icons.Default.LightMode else Icons.Default.DarkMode, "تغییر حالت نمایش") }
          }
        ) },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = { Surface(tonalElevation = 3.dp) { Row(Modifier.padding(10.dp).imePadding(), verticalAlignment = Alignment.CenterVertically) {
          OutlinedTextField(value = name, onValueChange = { name = it }, modifier = Modifier.weight(1f), singleLine = true, label = { Text("نام شخص") })
          FilterChip(selected = true, onClick = { newGender = if (newGender == "خانم") "آقا" else if (newGender == "آقا") "بدون جنسیت" else "خانم" }, label = { Text(newGender) }, leadingIcon = { Icon(Icons.Default.Person, null, Modifier.size(18.dp)) })
          FilledIconButton(onClick = { val n = name.trim(); if (n.isNotEmpty() && people.none { it.name == n }) { people = people + Person(n, newGender); name = ""; persist() } }) { Icon(Icons.Default.Add, "افزودن") }
          IconButton(onClick = { bulkDialog = true }) { Icon(Icons.Default.GroupAdd, "افزودن گروهی") }
        } } }
      ) { pad ->
        LazyColumn(Modifier.padding(pad).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
          item { Text("مدیریت نوبت‌دهی و قرعه‌کشی", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp)) }
          item { AnimatedVisibility(visible = searchOpen, enter = expandVertically(spring()) + fadeIn(), exit = shrinkVertically(spring()) + fadeOut()) { OutlinedTextField(value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth().animateContentSize(), singleLine = true, label = { Text("جست‌وجو در افراد") }, leadingIcon = { Icon(Icons.Default.Search, null) }, trailingIcon = { IconButton(onClick = { query = "" }) { Icon(Icons.Default.Clear, "پاک کردن") } }) } }
          item { Card { Column(Modifier.padding(16.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { Text("افراد (${people.size})", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f)); IconButton(onClick = { showList = !showList }) { Icon(if(showList) Icons.Default.VisibilityOff else Icons.Default.Visibility, null) } }
            AnimatedVisibility(showList, enter = expandVertically(spring()) + fadeIn(), exit = shrinkVertically() + fadeOut()) {
              if (filtered.isEmpty()) {
                Column(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                  Icon(Icons.Default.People, null, Modifier.size(42.dp), tint = MaterialTheme.colorScheme.primary)
                  Spacer(Modifier.height(8.dp)); Text(if (people.isEmpty()) "هنوز هیچ فردی اضافه نشده است" else "نتیجه‌ای پیدا نشد.")
                  if (people.isEmpty()) Text("اولین نفر را از نوار پایین اضافه کنید.", style = MaterialTheme.typography.bodySmall)
                }
              } else Column(Modifier.fillMaxWidth().heightIn(max = 246.dp).verticalScroll(rememberScrollState()).animateContentSize(spring())) {
                filtered.forEach { p -> PersonLine(p, onEdit = { editTarget = p }, onDelete = { deleteTarget = p }) }
              }
            }
          } } }
          item { Card { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) { Text("تنظیمات قرعه‌کشی", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f)); Text(emoji, fontSize = 24.sp); TextButton(onClick = { showSettings = !showSettings }) { Text(if (showSettings) "بستن" else "تنظیم") } }
            AnimatedVisibility(visible = showSettings, enter = expandVertically(spring()) + fadeIn(), exit = shrinkVertically(spring()) + fadeOut()) { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedTextField(value = emoji, onValueChange = { emoji = it.take(8); persist() }, label = { Text("ایموجی") }, singleLine = true)
              OutlinedButton(onClick = { showDatePicker = true }) { Icon(Icons.Default.DateRange, null); Spacer(Modifier.width(8.dp)); Text("تاریخ شروع: ${jalali(startDate)}") }
              SingleChoiceSegmentedButtonRow { listOf("رسمی", "مینیمال", "تلگرام").forEachIndexed { i, t -> SegmentedButton(selected = template == t, onClick = { template=t; persist() }, shape = SegmentedButtonDefaults.itemShape(i, 3)) { Text(t) } } }
            } }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Button(onClick = { generate(false) }, enabled = people.isNotEmpty(), modifier = Modifier.weight(1f)) { Icon(Icons.Default.Refresh, null); Spacer(Modifier.width(6.dp)); Text("ترتیب جدید") }; OutlinedButton(onClick = { generate(true) }, enabled = people.isNotEmpty() && previousSeed != 0L, modifier = Modifier.weight(1f)) { Text("Seed قبلی") } }
          } } }
          if (rows.isNotEmpty()) item { Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) { Column(Modifier.padding(16.dp)) { Text("خروجی برنامه", style = MaterialTheme.typography.titleLarge); Spacer(Modifier.height(8.dp)); Text(output); Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { TextButton(onClick = { clipboard.setText(AnnotatedString(output)) }) { Icon(Icons.Default.ContentCopy, null); Text("کپی") }; TextButton(onClick = { context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, output), "اشتراک‌گذاری")) }) { Icon(Icons.AutoMirrored.Filled.Send, null); Text("اشتراک‌گذاری") } } } } }
        }
      }
    }
    if (bulkDialog) BulkDialog(onDismiss = { bulkDialog=false }, onAdd = { text -> val existing=people.map { it.name }.toSet(); people = people + text.split(Regex("[,\\n]" )).map { it.trim() }.filter { it.isNotEmpty() && it !in existing }.distinct().map { Person(it) }; persist(); bulkDialog=false })
    if (editTarget != null) EditDialog(editTarget!!, onDismiss = { editTarget=null }, onSave = { updated -> people = people.map { if(it == editTarget) updated else it }; persist(); editTarget=null })
    if (deleteTarget != null) AlertDialog(onDismissRequest = { deleteTarget=null }, title = { Text("حذف شخص") }, text = { Text("${deleteTarget!!.name} حذف شود؟") }, confirmButton = { TextButton(onClick = { val removed=deleteTarget!!; people=people-filterSetOf(removed); persist(); deleteTarget=null }) { Text("حذف") } }, dismissButton = { TextButton(onClick = { deleteTarget=null }) { Text("انصراف") } })
    if (showDatePicker) { val state=rememberDatePickerState(initialSelectedDateMillis=startDate.toEpochDay()*86400000); DatePickerDialog(onDismissRequest={showDatePicker=false}, confirmButton={ TextButton(onClick={ state.selectedDateMillis?.let { startDate=LocalDate.ofEpochDay(it/86400000); persist() }; showDatePicker=false }) { Text("تأیید") } }, dismissButton={TextButton(onClick={showDatePicker=false}){Text("انصراف")}}) { DatePicker(state=state) } }
  }

@Composable fun PersonLine(p: Person, onEdit:()->Unit, onDelete:()->Unit) {
  ElevatedCard(modifier=Modifier.fillMaxWidth().padding(vertical=4.dp).animateContentSize(spring()), shape=RoundedCornerShape(20.dp)) {
    Row(Modifier.fillMaxWidth().padding(horizontal=12.dp, vertical=10.dp), verticalAlignment=Alignment.CenterVertically) {
      Surface(color=MaterialTheme.colorScheme.primaryContainer, shape=RoundedCornerShape(16.dp), modifier=Modifier.size(46.dp)) { Box(contentAlignment=Alignment.Center) { Text(p.name.take(1), style=MaterialTheme.typography.titleLarge, fontWeight=FontWeight.Bold) } }
      Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(p.name, fontWeight=FontWeight.SemiBold); AssistChip(onClick={}, label={Text(p.gender)}, leadingIcon={Icon(Icons.Default.Person, null, Modifier.size(16.dp))}) }
      IconButton(onClick=onEdit, modifier=Modifier.size(48.dp)){Icon(Icons.Default.Edit,"ویرایش ${p.name}")}; IconButton(onClick=onDelete, modifier=Modifier.size(48.dp)){Icon(Icons.Default.Delete,"حذف ${p.name}")}
    }
  }
}
@Composable fun BulkDialog(onDismiss:()->Unit, onAdd:(String)->Unit) { var text by remember{mutableStateOf("")}; AlertDialog(onDismissRequest=onDismiss,title={Text("افزودن گروهی")},text={OutlinedTextField(value=text,onValueChange={text=it},label={Text("نام‌ها با کاما یا خط جدید")},minLines=4)},confirmButton={TextButton(onClick={onAdd(text)}){Text("افزودن")}},dismissButton={TextButton(onClick=onDismiss){Text("انصراف")}}) }
@Composable fun EditDialog(person:Person,onDismiss:()->Unit,onSave:(Person)->Unit) { var n by remember{mutableStateOf(person.name)}; var g by remember{mutableStateOf(person.gender)}; AlertDialog(onDismissRequest=onDismiss,title={Text("ویرایش شخص")},text={Column { OutlinedTextField(n,{n=it},label={Text("نام")}); listOf("خانم","آقا","بدون جنسیت").forEach { Row(verticalAlignment=Alignment.CenterVertically){RadioButton(g==it,{g=it});Text(it)} } }},confirmButton={TextButton(onClick={if(n.trim().isNotEmpty())onSave(Person(n.trim(),g))}){Text("ذخیره")}},dismissButton={TextButton(onClick=onDismiss){Text("انصراف")}}) }
fun loadPeople(p:android.content.SharedPreferences):List<Person> = p.getString("people", "")!!.split("\\u001e").filter{it.isNotBlank()}.map { val x=it.split("\\u001f"); Person(x[0],x.getOrElse(1){"خانم"}) }
fun filterSetOf(p:Person)=setOf(p)
fun formatOutput(rows:List<ScheduleRow>,emoji:String,t:String):String = rows.mapIndexed { i,r -> val g=if(r.person.gender=="بدون جنسیت") "" else "${r.person.gender} "; when(t){"مینیمال"->"$emoji ${i+1}. ${r.person.name} — ${jalali(r.date)}";"تلگرام"->"$emoji *نفر ${ordinal(i+1)}* ${g}${r.person.name}\n📅 ${weekday(r.date)} ${jalali(r.date)}";else->"$emoji نفر ${ordinal(i+1)} ${g}${r.person.name} — ${weekday(r.date)} ${jalali(r.date)}"} }.joinToString("\n\n")
fun ordinal(n:Int)=listOf("اول","دوم","سوم","چهارم","پنجم","ششم","هفتم","هشتم","نهم","دهم").getOrElse(n-1){n.toString()}
fun weekday(d:LocalDate)=arrayOf("دوشنبه","سه‌شنبه","چهارشنبه","پنج‌شنبه","جمعه","شنبه","یکشنبه")[d.dayOfWeek.value-1]
// Persian (Jalali) conversion, no network or external calendar dependency.
fun jalali(d:LocalDate):String { var gy=d.year-1600; var gm=d.monthValue-1; var gd=d.dayOfMonth-1; val gdm=intArrayOf(31,28,31,30,31,30,31,31,30,31,30,31); var days=365*gy+(gy+3)/4-(gy+99)/100+(gy+399)/400; for(i in 0 until gm) days+=gdm[i]; if(gm>1 && ((gy%4==0&&gy%100!=0)||gy%400==0))days++; days+=gd; var j=days-79; val jp=j/12053; j%=12053; var jy=979+33*jp+4*(j/1461); j%=1461; if(j>=366){jy+=(j-1)/365;j=(j-1)%365}; val jm=if(j<186)j/31+1 else (j-186)/30+7; val jd=if(j<186)j%31+1 else (j-186)%30+1; return "%04d/%02d/%02d".format(jy,jm,jd) }
