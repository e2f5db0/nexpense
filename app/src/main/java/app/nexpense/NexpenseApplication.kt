// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 e2f5db0

package app.nexpense

import android.app.Application
import app.nexpense.data.DeviceKeyVault
import app.nexpense.data.GroupRepository
import app.nexpense.data.NexpenseDatabase
import app.nexpense.data.SyncWorker

class NexpenseApplication : Application() {
    lateinit var repository: GroupRepository
        private set
    lateinit var database: NexpenseDatabase
        private set

    override fun onCreate() {
        super.onCreate()
        database = NexpenseDatabase.create(this)
        repository = GroupRepository(this, database.dao(), DeviceKeyVault(this))
        SyncWorker.schedule(this)
    }
}
