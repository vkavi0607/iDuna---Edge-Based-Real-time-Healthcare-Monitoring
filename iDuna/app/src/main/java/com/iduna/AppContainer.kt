package com.iduna

import android.content.Context
import com.iduna.data.ble.BleHeartRateManager
import com.iduna.data.local.IdunaDatabase
import com.iduna.data.repository.IdunaRepository
import com.iduna.util.AlertNotifier
import com.iduna.util.PdfReportGenerator

class AppContainer(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val database = IdunaDatabase.create(appContext)
    private val bleHeartRateManager = BleHeartRateManager(appContext)
    private val alertNotifier = AlertNotifier(appContext)
    private val pdfReportGenerator = PdfReportGenerator(appContext)

    val repository = IdunaRepository(
        bleHeartRateManager = bleHeartRateManager,
        heartRateDao = database.heartRateDao(),
        alertEventDao = database.alertEventDao(),
        userProfileDao = database.userProfileDao(),
        userSettingsDao = database.userSettingsDao(),
        alertNotifier = alertNotifier,
        pdfReportGenerator = pdfReportGenerator,
    )

    init {
        alertNotifier.createChannels()
    }
}
