package com.kk.reminders

import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.media.RingtoneManager
import android.os.Bundle
import android.os.Parcel
import android.os.Parcelable
import android.preference.PreferenceManager
import android.support.v4.app.NotificationCompat
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.widget.TextView
import kotlinx.android.synthetic.main.activity_main.*
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val pref = PreferenceManager.getDefaultSharedPreferences(this)
        val b = pref.getBoolean("reminders", false)
        val rem = if (b) {
            Reminder(pref.getString("reminderTime", "12:00 PM"),
                    pref.getStringSet("reminderDays", setOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")).toList())
            } else {
            Reminder("12:00 PM", listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"))
        }


        time_value_text.text = rem.time
        arrayOf(sun_check, mon_check, tue_check, wed_check,
                thu_check, fri_check, sat_check).forEach { ctv ->
            if (rem.days.contains(ctv.text)) ctv.isChecked = true
            ctv.setOnClickListener { t ->
                if (ctv.isChecked)
                    ctv.isChecked = false
                else
                    ctv.isChecked = true
            }
        }

        time_value_text.setOnClickListener {
            val t = (it as? TextView)?.text ?: "12:00 PM"
            val sdf = SimpleDateFormat("hh:mm a")

            val c = Calendar.getInstance()
            c.time = sdf.parse(t as? String)

            val d = TimePickerDialog(this@MainActivity, TimePickerDialog.OnTimeSetListener { timePicker, i, j ->
                c.set(Calendar.HOUR_OF_DAY, i)
                c.set(Calendar.MINUTE, j)
                (it as? TextView)?.text = sdf.format(c.time)
            }, c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), false)

            with(d) {
                d.setTitle("Time")
                d.show()
            }
        }

        set_reminders_button.setOnClickListener {
            val time = time_value_text.text as String
            val days = mutableListOf<String>()

            arrayOf(sun_check, mon_check, tue_check, wed_check,
                    thu_check, fri_check, sat_check).forEach {
                if (it.isChecked)
                    days.add(it.text.toString())
            }

            val r = Reminder(time, days)

            val alarm = ReminderAlarmReceiver()
            alarm.cancelAlarm(this@MainActivity)
            alarm.setAlarm(r, this@MainActivity)

            // Save the alarm into SharedPreferences to account for reboot
            val pref = PreferenceManager.getDefaultSharedPreferences(this)
            with(pref.edit()) {
                putBoolean("reminders", true)
                putString("reminderTime", r.time)
                putStringSet("reminderDays", r.days.toSet())
                commit()
            }
            val a = AlertDialog.Builder(this).create()
            a.setTitle("Saved!")
            a.setMessage("The reminders were saved successfully.")
            a.setButton(AlertDialog.BUTTON_NEUTRAL, "OK", DialogInterface.OnClickListener { dialogInterface, i ->
            });
            a.show()
       }

        cancel_reminders_button.setOnClickListener {
            val alarm = ReminderAlarmReceiver()
            alarm.cancelAlarm(this@MainActivity)
            val a = AlertDialog.Builder(this).create()
            a.setTitle("Canceled!")
            a.setMessage("The reminders were canceled.")
            a.setButton(AlertDialog.BUTTON_NEUTRAL, "OK", DialogInterface.OnClickListener { dialogInterface, i ->
            });
        }
    }
}

data class Reminder(val time: String?, val days: List<String>) : Parcelable {
    constructor(source: Parcel): this(source.readString(), source.createStringArrayList())

    override fun describeContents() = 0

    override fun writeToParcel(dest: Parcel?, flags: Int) {
        dest?.writeString(time)
        dest?.writeStringList(days)
    }

    companion object {
        @JvmField val CREATOR: Parcelable.Creator<Reminder> = object : Parcelable.Creator<Reminder> {
            override fun createFromParcel(source: Parcel): Reminder = Reminder(source)
            override fun newArray(size: Int): Array<Reminder?> = arrayOfNulls(size)
        }
    }
}

class ReminderAlarmReceiver : BroadcastReceiver() {

    val notificationId: Int
        get() {
            return ((Date().time / 1000L) % Int.MAX_VALUE).toInt()
        }

    override fun onReceive(ctx: Context?, intent: Intent?) {

        if ("android.intent.action.BOOT_COMPLETED".equals(intent?.getAction())) {
            val pref = PreferenceManager.getDefaultSharedPreferences(ctx)
            val b = pref.getBoolean("reminders", false)
            if (b) {
                val rem = Reminder(pref.getString("reminderTime", "12:00 PM"),
                        pref.getStringSet("reminderDays", setOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")).toList())

                setAlarm(rem, ctx!!)
            }
        } else {
            Log.d("ALARM", "TRIGGERED")
            intent?.setExtrasClassLoader(Reminder::class.java.classLoader)
            val rem = intent?.getParcelableExtra<Reminder>("remParcel")

            val title = "Lunch Reminder"
            val text = "Its time to eat lunch!"
            val nm = ctx?.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager

            val mainIntent = Intent(ctx, MainActivity::class.java)
            mainIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            val pendingIntent = PendingIntent.getActivity(ctx, 0, mainIntent, PendingIntent.FLAG_UPDATE_CURRENT)
            val alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

            val builder = NotificationCompat.Builder(ctx)
                    .setSmallIcon(R.drawable.notification_template_icon_bg)
                    .setContentTitle(title)
                    .setContentText(text)
                    .setSound(alarmSound)
                    .setContentIntent(pendingIntent)

            nm?.notify(notificationId , builder.build())

            Log.d("NOTIFICATION", "SENT")
            setAlarm(rem!!, ctx!!)
        }
    }

    fun cancelAlarm(ctx: Context) {
        val alarmIntent = Intent(ctx, ReminderAlarmReceiver::class.java)

        val pendingIntent = PendingIntent.getBroadcast(ctx, 0 , alarmIntent, PendingIntent.FLAG_UPDATE_CURRENT)
        val alarmMgr = ctx.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        alarmMgr?.cancel(pendingIntent)

    }

    fun setAlarm(rems: Reminder, ctx: Context) {
        // We just want to set the alarm for the first upcoming
        val alarmIntent = Intent(ctx, ReminderAlarmReceiver::class.java)
        alarmIntent.putExtra("remParcel", rems)

        val pendingIntent = PendingIntent.getBroadcast(ctx, 0 , alarmIntent, PendingIntent.FLAG_UPDATE_CURRENT)
        // Calculate when the next alarm should go off based on the days and time.
        val sdf = SimpleDateFormat("MM/dd/yyyy hh:mm a")
        val sdf1 = SimpleDateFormat("MM/dd/yyyy")
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = System.currentTimeMillis()

        while(true) {
            var alarmCalendar: Calendar? = null
            val d = calendar.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.SHORT, Locale.US)
            if (rems.days.contains(d)) {
                val begin = sdf.parse(sdf1.format(calendar.time) + " " + rems.time).toCalendar()
                if (begin.after(calendar)) {
                    alarmCalendar = begin
                }
            }
            if (alarmCalendar == null) {
                calendar.add(Calendar.DATE, 1)
                // Truncate time.
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
            }
            else {
                val alarmMgr = ctx.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
                Log.d("SETTING ALARM", alarmCalendar.toString())
                alarmMgr?.setExact(AlarmManager.RTC_WAKEUP, alarmCalendar.timeInMillis, pendingIntent)
                break
            }
        }
    }
}

fun Date.toCalendar() : Calendar {
    val c = Calendar.getInstance()
    c.time = this
    return c
}

