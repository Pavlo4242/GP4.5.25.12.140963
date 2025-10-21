package com.grindrplus.core

import com.grindrplus.core.Logger
import com.grindrplus.core.LogSource
import com.grindrplus.bridge.BridgeClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import android.content.Context
import android.database.sqlite.SQLiteDatabase

//object DatabaseManager {
//    fun initializeDatabaseIfNeeded(context: Context) {
//        CoroutineScope(Dispatchers.IO).launch {
//            Logger.d("Checking if HTTP database needs initialization...", LogSource.MODULE)
//
//            val bridgeClient = BridgeClient(context)
//            val dbFile = bridgeClient.getHttpDbFile()
//
//            if (dbFile != null) {
//                if (!dbFile.exists()) {
//                    try {
//                        // Creates the file if it doesn't exist and ensures the directory is there.
//                        val db = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
//                        db.close()
//                        Logger.i("HTTP log database file created at: ${dbFile.absolutePath}", LogSource.MODULE)
//                    } catch (e: Exception) {
//                        Logger.e("Failed to create the database file: ${e.message}", LogSource.MODULE)
//                    }
//                } else {
//                    Logger.d("HTTP log database file already exists.", LogSource.MODULE)
//                }
//                HttpBodyLogger.initializeDatabase()
//            } else {
//                Logger.e("Failed to initialize or find the database file.", LogSource.MODULE)
//            }
//        }
//    }
//}

object DatabaseManager {
    fun initializeDatabaseIfNeeded(context: Context) {
        // Run on a background thread to avoid blocking the UI
        CoroutineScope(Dispatchers.IO).launch {
            // Change starts here
            Logger.d("Checking if HTTP database needs initialization...", LogSource.MODULE)

            // The BridgeClient handles connecting to the service.
            val bridgeClient = BridgeClient(context)

            // Calling getHttpDbFile() will get the path from the BridgeService.
            val dbFile = bridgeClient.getHttpDbFile()

            if (dbFile != null) {
                // The schema initialization is now also handled here to centralize logic.
                // This will create the DB file if it doesn't exist AND create the table.
                HttpBodyLogger.initializeDatabase()
                Logger.i("HTTP Database initialization check complete. Path: ${dbFile.absolutePath}", LogSource.MODULE)
            } else {
                Logger.e("Failed to initialize or find the database file.", LogSource.MODULE)
            }
            // Change ends here. The following lines are for context.
        }
    }
}