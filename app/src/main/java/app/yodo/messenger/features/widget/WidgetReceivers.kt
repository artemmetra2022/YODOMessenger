package app.yodo.messenger.features.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

class UnreadWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = UnreadWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WidgetScheduler.ensurePeriodic(context)
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        WidgetScheduler.requestRefresh(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        WidgetScheduler.cancelIfUnused(context)
    }
}

class WeatherWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WeatherWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WidgetScheduler.ensurePeriodic(context)
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        WidgetScheduler.requestRefresh(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        WidgetScheduler.cancelIfUnused(context)
    }
}

class CombinedWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CombinedWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WidgetScheduler.ensurePeriodic(context)
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        WidgetScheduler.requestRefresh(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        WidgetScheduler.cancelIfUnused(context)
    }
}

class SchoolWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SchoolWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        // Периодическое обновление нужно только ради даты каникул от админа (нужна сеть);
        // смену урока по звонку делает SchoolTicker и без сети.
        WidgetScheduler.ensurePeriodic(context)
        SchoolTicker.schedule(context)
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        WidgetScheduler.requestRefresh(context)
        // onUpdate приходит и после перезагрузки телефона — так цепочка тиков восстанавливается.
        SchoolTicker.schedule(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        WidgetScheduler.cancelIfUnused(context)
        SchoolTicker.cancelIfUnused(context)
    }
}
